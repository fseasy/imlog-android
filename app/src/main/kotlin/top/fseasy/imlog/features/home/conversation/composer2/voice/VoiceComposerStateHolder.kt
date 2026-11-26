package top.fseasy.imlog.features.home.conversation.composer2.voice

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.common.TransitionDecision
import top.fseasy.imlog.common.TransitionResult
import top.fseasy.imlog.common.launchCatching
import top.fseasy.imlog.domain.audio.AudioRecordError
import top.fseasy.imlog.domain.audio.AudioRecordException
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.MergeFailureReason
import top.fseasy.imlog.domain.audio.RecordingPrompt
import top.fseasy.imlog.domain.audio.RecordingSessionState
import top.fseasy.imlog.domain.audio.VoiceDraftAudioProcessor
import top.fseasy.imlog.domain.audio.VoiceRecordingCoordinator
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.model.authenticatedIdOrNull
import top.fseasy.imlog.domain.model.launchUntilAuth
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.usecase.composer.CommitOnRecordingSuccessResult
import top.fseasy.imlog.domain.usecase.composer.VoiceDraftUseCase
import top.fseasy.imlog.domain.usecase.composer.sendattachment.SendVoiceMessageUseCase
import top.fseasy.imlog.domain.usecase.pathpolicy.VoiceDraftPathUseCase
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat
import top.fseasy.imlog.features.home.conversation.ConversationUiEvent

/**
 * 1. subscribe the [composerMode] to get the status.
 * 2. It's not autocloseable, as the final draft state must be handled within business logic. call
 *    onTeardown
 */
class VoiceComposerStateHolder(
    private val topicId: TopicId,
    private val authState: StateFlow<AuthState>,
    private val storageRepository: StorageRepository,
    private val topicRepository: TopicRepository,
    private val sendVoiceMessageUseCase: SendVoiceMessageUseCase,
    private val draftPathUseCase: VoiceDraftPathUseCase,
    draftAudioProcessor: VoiceDraftAudioProcessor,
    private val coordinator: VoiceRecordingCoordinator,
    private val onSendUiEvent: (ConversationUiEvent) -> Unit,
    private val viewModelScope: CoroutineScope, // 由 ComposerViewModel 的 viewModelScope 注入
    private val appScope:
        CoroutineScope, // App level scope, used for launching cancel/stop, saving draft
) {
  private val draftUseCase =
      VoiceDraftUseCase(
          storageRepository = storageRepository,
          topicRepository = topicRepository,
          outputAudioFileExtension = coordinator.outputAudioFileExtension,
          draftPathUseCase = draftPathUseCase,
          audioProcessor = draftAudioProcessor,
      )

  private val draftStateStore =
      VoiceDraftUiStateStore(
          appScope = appScope,
          persistDraft = { userId, draft ->
            draftUseCase.saveDraftToDb(
                userId = userId,
                topicId = topicId,
                draft = draft,
            )
          },
      )
  private val draftState: StateFlow<VoiceDraftUiState>
    get() = draftStateStore.state

  val composerMode: StateFlow<VoiceComposerMode> =
      combine(coordinator.sessionState, _draftState) { session, draft ->
            when (session) {
              is RecordingSessionState.Locked ->
                  VoiceComposerMode.ActiveRecording(baseWaveform = draft?.base?.waveform)
              is RecordingSessionState.Holding -> VoiceComposerMode.Holding
              is RecordingSessionState.Idle if draft?.base != null ->
                  VoiceComposerMode.Draft(
                      VoicePlayerUiState(
                          durationText = draft.base.duration.toAppMessageTimeFormat(),
                          waveform = draft.base.waveform,
                          playbackProgress = 0f,
                          isPlaying = false,
                      ),
                  )
              else -> VoiceComposerMode.Idle
            }
          }
          .distinctUntilChanged()
          .stateIn(
              viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
              initialValue = VoiceComposerMode.Idle,
          )

  // 实时脉冲流（高频 30~60Hz：直接透传 coordinator 的 pulse）
  val currentPulse: Flow<Float> =
      coordinator.recordingSnapshot.map { it.currentPulse }.distinctUntilChanged()

  //  时长流（中频 1Hz：按秒节流，避免毫秒级重组）
  val displayDuration: Flow<Duration> =
      combine(
              coordinator.recordingSnapshot,
              _draftState,
          ) { snapshot, draft ->
            val base = draft?.base?.duration ?: Duration.ZERO
            base + snapshot.duration
          }
          .map { it.inWholeSeconds } // 只精确到秒，一秒只发射一次！
          .distinctUntilChanged()
          .map { it.seconds }

  init {
    viewModelScope.launchUntilAuth(authState, Dispatchers.IO) { userId ->
      val preparedVoiceDraft =
          draftUseCase.prepareVoiceDraftOnEnter(
              userId = userId,
              topicId = topicId,
          )
      draftStateStore.markInitialized(userId = userId, initialDraft = preparedVoiceDraft.draft)
      preparedVoiceDraft.recoveryNotice?.let {
        onSendUiEvent(ConversationUiEvent.ShowVoiceDraftRecoveryNotice(it))
      }
    }
  }

  fun onStartDirectLock() {
    startActiveRecordingOrRollback(
        sourceActionName = "DirectLock",
        canStartRecordingFrom = { current -> current is VoiceDraftUiState.Idle },
        nextStateProvider = { userId, _ ->
          val draft = draftUseCase.createBaseVoiceDraft(userId)
          VoiceDraftUiState.ActiveRecording.Locked(
              draft = draft,
              sessionToken = Any(),
              action = BeforeOsNativeStartedAction.StartOsNative,
          )
        },
        startRecordingExecutor = ::executeDirectLockRecording,
    )
  }

  fun onPauseLocked() = onPauseLocked(null)

  /** when pause action, stop recording, commit draft if it has. */
  private fun onPauseLocked(osEventSession: RecordingOsEventSession?) {
    val currentState =
        tryTransitStop(
            osEventSession = osEventSession,
        ) ?: return

    pauseLockedWithTransitedState(currentState, osEventSession)
  }

  /** when pause action, stop recording, commit draft if it has. */
  private fun pauseLockedWithTransitedState(
      state: VoiceDraftUiState.ActiveRecording,
      osEventSession: RecordingOsEventSession?,
  ) {
    launchStopActiveRecording(
        currentState = state,
        osEventSession = osEventSession,
        onSuccessNextStateProvider = { committedDraft ->
          VoiceDraftUiState.CommittedDraft(committedDraft)
        },
        onCommitDraftSuccess = null,
    )
  }

  fun onResumeLocked() {
    startActiveRecordingOrRollback(
        sourceActionName = "ResumeLocked",
        canStartRecordingFrom = { current -> current is VoiceDraftUiState.CommittedDraft },
        nextStateProvider = { userId, currentState ->
          // Just build object in Memory, no side effects
          val draft = (currentState as VoiceDraftUiState.CommittedDraft).draft
          val appendingDraft =
              draftUseCase.createAppendingVoiceDraft(baseDraft = draft, userId = userId)
          VoiceDraftUiState.ActiveRecording.Locked(
              draft = appendingDraft,
              sessionToken = Any(),
              action = BeforeOsNativeStartedAction.StartOsNative,
          )
        },
        startRecordingExecutor = ::executeDirectLockRecording,
    )
  }

  /** Locked 面板下，点击【发送】（无论是正在录音中点发送，还是在草稿态点发送） */
  fun onSendLocked() {
    // 2 condition: 1. in committed state 2. in ActiveRecording state.
    // Generally, we should transit in one [tryTransition], but to resue the tryStop, let's check it
    // in sequential.
    // It should be ok.

    fun transitIfInCommittedDraftState(): Boolean {
      val transitionResult = draftStateStore.tryTransition { current ->
        if (current is VoiceDraftUiState.CommittedDraft)
            TransitionDecision.Next(VoiceDraftUiState.Idle)
        else TransitionDecision.Skip
      }
      if (transitionResult !is TransitionResult.Done) {
        return false
      }
      val recordingState = transitionResult.previous as VoiceDraftUiState.CommittedDraft
      appScope.launchCatching { sendCommittedDraft(recordingState.draft) }
      return true
    }
    if (transitIfInCommittedDraftState()) return
    val currentActiveRecordingState = tryTransitStop(osEventSession = null) ?: return
    // do stop
    launchStopActiveRecording(
        currentState = currentActiveRecordingState,
        osEventSession = null,
        onSuccessNextStateProvider = { VoiceDraftUiState.Idle },
        onCommitDraftSuccess = { committedDraft -> sendCommittedDraft(committedDraft) },
    )
  }

  fun onCancelLocked() {
    /**
     * SAME as [onSendLocked], 2 condition: 1. in committed state 2. in ActiveRecording state.
     *
     * We still reuse the [launchCancelActiveRecording]
     */
    fun transitIfInCommittedDraftState(): Boolean {
      val transitionResult = draftStateStore.tryTransition { current ->
        if (current is VoiceDraftUiState.CommittedDraft)
            TransitionDecision.Next(VoiceDraftUiState.Idle)
        else TransitionDecision.Skip
      }
      if (transitionResult !is TransitionResult.Done) {
        return false
      }
      val recordingState = transitionResult.previous as VoiceDraftUiState.CommittedDraft
      appScope.launchCatching {
        draftStateStore.persistLatestNow()
        draftUseCase.deleteDraftFiles(recordingState.draft)
      }
      return true
    }
    if (transitIfInCommittedDraftState()) return
    val currentState = tryTransitCancel() ?: return
    launchCancelActiveRecording(currentState)
  }

  @OptIn(ExperimentalAtomicApi::class)
  fun onStartHolding() {
    startActiveRecordingOrRollback(
        "hold",
        canStartRecordingFrom = { current -> current is VoiceDraftUiState.Idle },
        nextStateProvider = { userId, _ ->
          val draft = draftUseCase.createBaseVoiceDraft(userId)
          VoiceDraftUiState.ActiveRecording.Holding(
              draft = draft,
              sessionToken = Any(),
              action = BeforeOsNativeStartedAction.StartOsNative,
          )
        },
        startRecordingExecutor = { destinationPath, sessionToken ->
          coordinator.start(
              destinationPath = destinationPath,
              onOsEvent = { event ->
                val osEventSession = RecordingOsEventSession(event, sessionToken)
                // When callback, hold may have changed to Locked.
                when (val stoppedState = tryTransitStop(osEventSession)) {
                  is VoiceDraftUiState.ActiveRecording.Holding -> {
                    stopHoldingWithTransitedState(stoppedState, osEventSession)
                  }
                  is VoiceDraftUiState.ActiveRecording.Locked -> {
                    pauseLockedWithTransitedState(stoppedState, osEventSession)
                  }
                  null -> {
                    Timber.d("OsEvent [$osEventSession] ignored, state = ${draftState.value}")
                  }
                }
              },
          )
        },
    )
  }

  /** Holding -> Locked */
  fun onLockHolding() {
    val transitionResult = draftStateStore.tryTransition { current ->
      if (current is VoiceDraftUiState.ActiveRecording.Holding) {
        val nextState = current.toLockedIfPossible() ?: return@tryTransition TransitionDecision.Skip
        return@tryTransition TransitionDecision.Next(nextState)
      }
      return@tryTransition TransitionDecision.Skip
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.d("holdToLock was blocked on state = ${draftState.value}")
      return
    }
    // NOTE: it's a sync fun
    coordinator.guarding(RecordingPrompt.Default)
  }

  fun onCancelHolding() {
    val currentState = tryTransitCancel() ?: return
    launchCancelActiveRecording(currentState)
  }

  private fun onStopHolding() {
    val currentState = tryTransitStop(null) ?: return
    stopHoldingWithTransitedState(currentState, null)
  }

  /** transit state first, then call this stoping logic */
  private fun stopHoldingWithTransitedState(
      state: VoiceDraftUiState.ActiveRecording,
      osEventSession: RecordingOsEventSession?,
  ) {
    launchStopActiveRecording(
        currentState = state,
        osEventSession = osEventSession,
        onSuccessNextStateProvider = { VoiceDraftUiState.Idle },
        onCommitDraftSuccess = { committedDraft -> sendCommittedDraft(committedDraft) },
    )
  }

  private suspend fun executeDirectLockRecording(
      destinationPath: StoragePathModel.Internal,
      sessionToken: Any,
  ) =
      coordinator.startAndGuarding(
          prompt = RecordingPrompt.Default,
          destinationPath = destinationPath,
          onOsEvent = { event ->
            onPauseLocked(RecordingOsEventSession(event, sessionToken))
          },
      )

  /** @param nextStateProvider param `currentState` is a state that meets [canStartRecordingFrom] */
  private fun startActiveRecordingOrRollback(
      sourceActionName: String,
      canStartRecordingFrom: (VoiceDraftUiState) -> Boolean,
      nextStateProvider:
          (userId: UserId, currentState: VoiceDraftUiState) -> VoiceDraftUiState.ActiveRecording,
      startRecordingExecutor:
          suspend (destinationPath: StoragePathModel.Internal, sessionToken: Any) -> Result<Unit>,
  ) {
    val userId = authState.value.authenticatedIdOrNull() ?: return
    val transition = draftStateStore.tryTransition { current ->
      if (canStartRecordingFrom(current)) {
        val nextState = nextStateProvider(userId, current)
        TransitionDecision.Next(nextState)
      } else {
        TransitionDecision.Skip
      }
    }
    if (transition !is TransitionResult.Done) {
      Timber.d("$sourceActionName call is blocked as current state = ${draftState.value}")
      return
    }
    val triggerStartingState = transition.current as VoiceDraftUiState.ActiveRecording
    val sessionToken = triggerStartingState.sessionToken
    val recordingFilePath = triggerStartingState.draft.recordingFilePath

    viewModelScope.launchCatching {
      fun onIllegalState(env: String): Nothing {
        val currentState = draftState.value
        val msg = "$sourceActionName got IllegalState [$currentState] when $env"
        Timber.e(msg)
        throw IllegalStateException(msg)
      }
      try {
        val startResult = startRecordingExecutor(recordingFilePath, sessionToken)
        val startedSuccess = startResult.isSuccess
        val resultTransitionResult = draftStateStore.tryTransition { current ->
          if (
              current is VoiceDraftUiState.ActiveRecording &&
                  // Generally, we don't need to verify the sessionToken as we can't start a new
                  // session
                  // before native started.
                  current.sessionToken == triggerStartingState.sessionToken
          ) {
            val nextState =
                current.nextOnceOsNativeStarted(startedSuccess)
                    ?: return@tryTransition TransitionDecision.Skip
            return@tryTransition TransitionDecision.Next(nextState)
          }
          TransitionDecision.Skip
        }

        if (resultTransitionResult !is TransitionResult.Done) {
          onIllegalState(
              "decide transition result after os-native started (success=$startedSuccess)",
          )
        }
        val resultState = resultTransitionResult.current as VoiceDraftUiState.ActiveRecording

        when (resultState.action) {
          BeforeOsNativeStartedAction.CancelOnceStarted,
          BeforeOsNativeStartedAction.StartOsNative,
          BeforeOsNativeStartedAction.StopOnceStarted -> onIllegalState("transit action done")
          OsNativeStartedAction.Recording -> Unit // the best result. do nothing
          // From [CancelUntileStarted] to [Cancel]
          OsNativeStartedAction.Cancel,
          // From [StopUntileStarted] to [Stop]
          // even it's stop, logically we know the audio must be countless as recorder will be
          // stopped
          // immediately.
          // But in case it success if we call `Stop`, here we call Cancel directly.
          // Then we have to provides the next state because of the unmatches
          // Tips have to be shown at state-set point, as here we can't get osEventSession
          // info.
          OsNativeStartedAction.Stop -> launchCancelActiveRecording(resultState)
          OsNativeStartFailedAction.RollbackOnStartFailed -> {
            // Show Tips when start failed
            startResult.onFailure { e ->
              (e as? AudioRecordException)?.error?.let { recordError ->
                onSendUiEvent(
                    ConversationUiEvent.ShowVoiceRecordingNotice(
                        VoiceRecordingNotice.StartFailure(
                            recordError,
                            isAppending = resultState.isActiveRecordingLockedAppending(),
                        ),
                    ),
                )
              }
            }
            launchCancelActiveRecording(resultState)
          }
          OsNativeStartFailedAction.CancelOnStartFailed,
          OsNativeStartFailedAction.StopOnStartFailed -> launchCancelActiveRecording(resultState)
        }
      } catch (e: Throwable) {
        // reason1: CancellationException
        // reason2: IllegalStateException
        Timber.w(e, "Failed when process coordinator started result, rollback if possible")
        (draftState.value as? VoiceDraftUiState.ActiveRecording)?.let {
          launchCancelActiveRecording(it)
        } // Else, it's Idle/CommittedDraft (should be impossible!!), no need to rollback though.
        throw e
      }
    }
  }

  private fun tryTransitCancel() =
      tryTransitCancelOrStop(
          null,
          true,
      )

  private fun tryTransitStop(
      osEventSession: RecordingOsEventSession?,
  ) =
      tryTransitCancelOrStop(
          osEventSession,
          false,
      )

  private fun tryTransitCancelOrStop(
      osEventSession: RecordingOsEventSession?,
      isCancel: Boolean,
  ): VoiceDraftUiState.ActiveRecording? {
    val transitionResult = draftStateStore.tryTransition { current ->
      if (
          // 没有必要再具体区分子类是 ActiveRecording.Hold or Locked. 因为
          // 1. UI 入口同一时刻只可能有 1 种 active recording mode
          // 2. 回调入口后面有 sessionToken 区分
          current is VoiceDraftUiState.ActiveRecording &&
              // 避免串台
              (osEventSession == null || osEventSession.sessionToken === current.sessionToken)
      ) {
        val nextState = if (isCancel) current.nextOnUserCancel() else current.nextOnUserStop()
        return@tryTransition if (nextState == null) TransitionDecision.Skip
        else TransitionDecision.Next(nextState)
      }
      return@tryTransition TransitionDecision.Skip
    }
    if (transitionResult !is TransitionResult.Done) {
      val target = if (isCancel) "Cancel" else "Stop"
      Timber.d("Transit to $target was blocked, state = ${draftState.value}")
      return null
    }
    val currentState = transitionResult.current as VoiceDraftUiState.ActiveRecording
    if (currentState.action is BeforeOsNativeStartedAction) {
      // OnRace ==> action is BeforeOsNativeStartedAction (use this we don't need to eval the
      // actual action)
      if (!isCancel) {
        // Only tips when STOP.
        onSendUiEvent(
            ConversationUiEvent.ShowVoiceRecordingNotice(
                VoiceRecordingNotice.StopFailure(
                    recordError = AudioRecordError.TooShort,
                    mergeFailureReason = null,
                    osEvent = osEventSession?.event,
                    couldRollback = currentState.isActiveRecordingLockedAppending(),
                ),
            ),
        )
      }
      return null
    }
    return currentState
  }

  /**
   * Cancel ActiveRecording on given current state, transit to Idle/Committed state based on the
   * [VoiceDraftUiState.ActiveRecording.action]
   *
   * This is called when:
   * 1. The user intentionally cancels the recording.
   * 2. A stop is requested (by the user or by an OS event), but the underlying recorder has not
   *    started or has failed, so it cannot be stopped normally.
   *
   * When entering this Cancel step, we assume _draftState had be frozen so we'll directly update
   * the state here.
   *
   * Use [appScope] to make sure it can always be canceled.
   */
  private fun launchCancelActiveRecording(
      currentState: VoiceDraftUiState.ActiveRecording,
  ) = appScope.launchCatching {
    // `Try` to handle CancellationException.
    try {
      if (currentState.action is OsNativeStartedAction) {
        coordinator.cancel()
      } else {
        coordinator.stopGuarding()
      }
    } finally {
      val commitResult = commitActiveRecordingOnCancelDone(currentState)
      // 用 NonCancellable 确保即使协程被取消，文件清理和状态复位也不会漏掉
      withContext(NonCancellable + Dispatchers.IO) {
        draftStateStore.updateAndPersist(commitResult.state)
        storageRepository.deleteFiles(commitResult.filesToDelete)
      }
    }
  }

  /**
   * A general stop function for active-recording.
   *
   * it'll stop the ActiveRecording and transit to Idle/Committed state based on the given nextState
   * provider.
   *
   * Once entering this, we assume the `_draftState` can't be updated anymore due to Guarding! So no
   * more check and directly update!
   *
   * Split action & state-provider in param to force caller providing the next state!
   *
   * @param onCommitDraftSuccess shouldn't throw any exception except CancellationException.
   */
  private fun launchStopActiveRecording(
      currentState: VoiceDraftUiState.ActiveRecording,
      osEventSession: RecordingOsEventSession?,
      onSuccessNextStateProvider: (committedDraft: VoiceDraft.Committed) -> VoiceDraftUiState,
      onCommitDraftSuccess: (suspend (committedDraft: VoiceDraft.Committed) -> Unit)?,
  ): Job? {
    val action = currentState.action
    if (action !is OsNativeStartedAction) {
      // recorder not started, stop is countless. route to Cancel with rollback
      return launchCancelActiveRecording(currentState)
    }
    val userId = authState.value.authenticatedIdOrNull() ?: return null
    val draft = currentState.draft
    return appScope.launchCatching {
      suspend fun executeOnFailure(
          recordError: AudioRecordError?,
          mergeFailureReason: MergeFailureReason? = null,
      ) {
        // When Failure, next State will always be rollback result (Idle/CommittedDraft)
        val commitResult = VoiceDraftUseCase.commitByRollbackOnRecordingFailure(draft)
        val nextState =
            if (commitResult.draft != null) VoiceDraftUiState.CommittedDraft(commitResult.draft)
            else VoiceDraftUiState.Idle
        withContext(NonCancellable) {
          // transit & persist first, then delete file
          draftStateStore.updateAndPersist(nextState)
          storageRepository.deleteFiles(commitResult.filesToDelete)
          onSendUiEvent(
              ConversationUiEvent.ShowVoiceRecordingNotice(
                  VoiceRecordingNotice.StopFailure(
                      recordError = recordError,
                      mergeFailureReason = mergeFailureReason,
                      osEvent = osEventSession?.event,
                      couldRollback = commitResult.draft != null,
                  ),
              ),
          )
        }
      }

      val stopResult =
          try {
            coordinator.stop()
          } catch (e: CancellationException) {
            // Have to rollback
            executeOnFailure(null)
            throw e
          }
      // Following Step should be safe for sate-transition and resource releasing without any
      // try-catch
      stopResult
          .onFailure { e ->
            Timber.w(e, "StopRecording failed, possibly AudioRecorder internal got Invalid State")
            executeOnFailure(null)
          }
          .onSuccess { audioRecordResult ->
            when (audioRecordResult) {
              is AudioRecordResult.Failure -> {
                executeOnFailure(audioRecordResult.error)
              }
              is AudioRecordResult.Success -> {
                withContext(NonCancellable) {
                  val commitResult =
                      draftUseCase.commitOnRecordingSuccess(
                          userId = userId,
                          draft = draft,
                          audioRecordResult = audioRecordResult,
                      )
                  when (commitResult) {
                    is CommitOnRecordingSuccessResult.Success -> {
                      // transit & persist first, then delete files.
                      val nextState = onSuccessNextStateProvider(commitResult.draft)
                      draftStateStore.updateAndPersist(nextState)
                      storageRepository.deleteFiles(commitResult.filesToDelete)
                      onCommitDraftSuccess?.invoke(commitResult.draft)
                      osEventSession?.let { eventSession ->
                        onSendUiEvent(
                            ConversationUiEvent.ShowVoiceRecordingNotice(
                                VoiceRecordingNotice.StopSuccessOnOsEvent(eventSession.event),
                            ),
                        )
                      }
                    }
                    is CommitOnRecordingSuccessResult.Failure -> {
                      Timber.e(
                          "Failed to commit on recording success, reason = ${commitResult.reason}",
                      )
                      executeOnFailure(null, commitResult.reason)
                    }
                  }
                }
              }
            }
          }
    }
  }

  suspend fun teardownWhenExit(): VoiceDraft? {
    val currentMode = composerMode.value
    return when (currentMode) {
      is VoiceComposerMode.ActiveRecording -> {
        // stop and generate draft
        when (val recordingResult = coordinator.onStopLocked()) {
          is AudioRecordResult.Failure -> {
            storageRepository.deleteFile(recordingResult.filePath)
            draftState.value
          }
          is AudioRecordResult.Success -> {
            mergeChunkIntoDraftInternal(recordingChunkResult = recordingResult)
          }
          null -> {
            Timber.w("Invalid state that get null under: [active-recording -> stop]")
            _draftState.value
          }
        }
      }
      is VoiceComposerMode.Draft -> currentMode.draft
      VoiceComposerMode.Holding -> {
        // cancel
        coordinator.cancel()?.let { storageRepository.deleteFile(it.filePath) }
        null
      }
      VoiceComposerMode.Idle -> TODO()
    }
  }

  /**
   * NO exception will be thrown!
   *
   * Main-thread safe
   */
  private suspend fun sendCommittedDraft(draft: VoiceDraft.Committed) {}
}
