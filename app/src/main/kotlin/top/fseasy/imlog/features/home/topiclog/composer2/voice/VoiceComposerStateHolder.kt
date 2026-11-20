package top.fseasy.imlog.features.home.topiclog.composer2.voice

import android.content.Context
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.common.TransitionDecision
import top.fseasy.imlog.common.TransitionResult
import top.fseasy.imlog.common.launchCatching
import top.fseasy.imlog.common.tryTransition
import top.fseasy.imlog.data.audio.AndroidAudioWaveformExtractor
import top.fseasy.imlog.domain.audio.AudioRecordError
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.RecordingOsEvent
import top.fseasy.imlog.domain.audio.RecordingPrompt
import top.fseasy.imlog.domain.audio.RecordingSessionState
import top.fseasy.imlog.domain.audio.VoiceRecordingCoordinator
import top.fseasy.imlog.domain.audio.WaveformCodec5Bit
import top.fseasy.imlog.domain.audio.deserializeToByteArray
import top.fseasy.imlog.domain.audio.serializeToString
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.model.authenticatedIdOrNull
import top.fseasy.imlog.domain.model.launchUntilAuth
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.usecase.composer.VoiceDraftUseCase
import top.fseasy.imlog.domain.usecase.composer.sendattachment.SendVoiceMessageUseCase
import top.fseasy.imlog.domain.usecase.pathpolicy.StoragePathUseCase
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat

/**
 * 1. subscribe the [composerMode] to get the status.
 * 2. It's not autocloseable, as the final draft state must be handled within business logic. call
 *    onTeardown
 */
class VoiceComposerStateHolder(
    private val topicId: TopicId,
    private val authState: StateFlow<AuthState>,
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    private val topicRepository: TopicRepository,
    private val sendVoiceMessageUseCase: SendVoiceMessageUseCase,
    private val coordinator: VoiceRecordingCoordinator,
    private val onShowSnackbar: (String) -> Unit,
    private val viewModelScope: CoroutineScope, // 由 ComposerViewModel 的 viewModelScope 注入
    private val appScope: CoroutineScope, // App level scope, used for saving draft
    private val context: Context,
) {

  private val _draftState = MutableStateFlow<VoiceDraftUiState>(VoiceDraftUiState.Idle)

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

  private val draftUseCase =
      VoiceDraftUseCase(
          storageRepository = storageRepository,
          topicRepository = topicRepository,
          sendVoiceMessageUseCase = sendVoiceMessageUseCase,
          outputAudioFileExtension = coordinator.outputAudioFileExtension,
          audioWaveformExtractor =
              AndroidAudioWaveformExtractor(
                  context = context,
                  storageRepository = storageRepository,
              ),
      )

  init {
    viewModelScope.launchUntilAuth(authState, Dispatchers.IO) {}
  }

  private suspend fun executeDirectLockRecording(
      destinationPath: StoragePathModel.Internal,
      sessionToken: Any,
  ) =
      coordinator.startAndGuarding(
          prompt = RecordingPrompt.Default,
          destinationPath = destinationPath,
          onOsEvent = { event ->
            onPauseLockedRecording(RecordingOsEventSession(event, sessionToken))
          },
      )

  fun onStartDirectLock() {
    startActiveRecordingOrRollback(
        sourceActionName = "DirectLock",
        canStartRecordingFrom = { current -> current is VoiceDraftUiState.Idle },
        nextStateProvider = { userId, _ ->
          val draft = draftUseCase.newVoiceDraft(userId)
          VoiceDraftUiState.ActiveRecording.Locked(
              draft = draft,
              sessionToken = Any(),
              action = ActiveRecordingAction.StartOsNative,
          )
        },
        startRecordingExecutor = ::executeDirectLockRecording,
        startFailureTipsProvider = { e ->
          "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
        },
    )
  }

  fun onPauseLockedRecording() = onPauseLockedRecording(null)

  /** when pause action, stop recording, commit draft if it has. */
  private fun onPauseLockedRecording(osEventTrigger: RecordingOsEventSession?) {

    val currentState =
        tryTransitStop(
            osEventTrigger = osEventTrigger,
        ) ?: return

    launchStopRecording(
        currentState = currentState,
        onFailureNextStateProvider = { committedDraft ->
          if (committedDraft != null) VoiceDraftUiState.CommittedDraft(committedDraft)
          else VoiceDraftUiState.Idle
        },
        onStopFailure = { committedDraft, recordError ->
          showTipsWhenStopError(recordError, osEventTrigger, couldRollback = committedDraft != null)
        },
        onSuccessNextStateProvider = { committedDraft ->
          VoiceDraftUiState.CommittedDraft(committedDraft)
        },
        onStopSuccess = null,
    )
  }

  fun onResumeLockedRecording() {
    startActiveRecordingOrRollback(
        sourceActionName = "ResumeLockedRecording",
        canStartRecordingFrom = { current -> current is VoiceDraftUiState.CommittedDraft },
        nextStateProvider = { userId, currentState ->
          // Just build object in Memory, no side effects
          val draft = (currentState as VoiceDraftUiState.CommittedDraft).draft
          val resumeDraft = draftUseCase.appending(baseDraft = draft, userId = userId)
          VoiceDraftUiState.ActiveRecording.Locked(
              draft = resumeDraft,
              sessionToken = Any(),
              action = ActiveRecordingAction.StartOsNative,
          )
        },
        startRecordingExecutor = ::executeDirectLockRecording,
        startFailureTipsProvider = { e ->
          "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
        },
    )
  }

  /** Locked 面板下，点击【发送】（无论是正在录音中点发送，还是在草稿态点发送） */
  fun onSendLocked() {
    fun ifInCommittedDraftState() {
      val transitionResult = _draftState.tryTransition { current ->
        if (current is VoiceDraftUiState.CommittedDraft)
            TransitionDecision.Next(VoiceDraftUiState.Idle)
        else TransitionDecision.Skip
      }
      if (transitionResult !is TransitionResult.Done) {
        return
      }
      val recordingState = transitionResult.previous as VoiceDraftUiState.CommittedDraft
      appScope.launchCatching { sendCommittedDraft(recordingState.draft) }
    }

    // If stopOnRace, don't show tips here
    val currentActiveRecordingState =
        tryTransitStop(
            osEventTrigger = null,
        ) ?: return ifInCommittedDraftState()
    // do stop
    launchStopRecording(
        currentState = currentActiveRecordingState,
        onFailureNextStateProvider = { committedDraft ->
          if (committedDraft != null) {
            // CurrentState is in Appending Mode. rollback to committedDraft
            VoiceDraftUiState.CommittedDraft(committedDraft)
          } else VoiceDraftUiState.Idle
        },
        onStopFailure = { committedDraft, recordError ->
          val couldRollback = committedDraft != null
          showTipsWhenStopError(
              recordError = recordError,
              osEventTrigger = null,
              couldRollback = couldRollback,
          )
        },
        onSuccessNextStateProvider = { VoiceDraftUiState.Idle },
        onStopSuccess = { committedDraft -> sendCommittedDraft(committedDraft) },
    )
  }

  /** @param nextStateProvider param `currentState` is a state that meets [canStartRecordingFrom] */
  private fun startActiveRecordingOrRollback(
      sourceActionName: String,
      canStartRecordingFrom: (VoiceDraftUiState) -> Boolean,
      nextStateProvider:
          (userId: UserId, currentState: VoiceDraftUiState) -> VoiceDraftUiState.ActiveRecording,
      startRecordingExecutor:
          suspend (destinationPath: StoragePathModel.Internal, sessionToken: Any) -> Result<Unit>,
      startFailureTipsProvider: (Throwable) -> String,
  ) {
    val userId = authState.value.authenticatedIdOrNull() ?: return
    val transition = _draftState.tryTransition { current ->
      if (canStartRecordingFrom(current)) {
        val nextState = nextStateProvider(userId, current)
        TransitionDecision.Next(nextState)
      } else {
        TransitionDecision.Skip
      }
    }
    if (transition !is TransitionResult.Done) {
      Timber.d("$sourceActionName call is blocked as current state = ${_draftState.value}")
      return
    }
    val triggerStartingState = transition.current as VoiceDraftUiState.ActiveRecording
    val sessionToken = triggerStartingState.sessionToken
    val recordingFilePath = triggerStartingState.draft.recordingFilePath

    viewModelScope.launchCatching {
      fun onIllegalState(env: String): Nothing {
        val currentState = _draftState.value
        val msg = "$sourceActionName got IllegalState [$currentState] when $env"
        Timber.e(msg)
        throw IllegalStateException(msg)
      }
      try {
        val startedSuccess = startRecordingExecutor(recordingFilePath, sessionToken).isSuccess

        val resultTransitionResult = _draftState.tryTransition { current ->
          (current as? VoiceDraftUiState.ActiveRecording)
              ?.nextStateAfterStartedRecorder(
                  startedSuccess,
                  triggerStartingState,
              )
              ?.let {
                TransitionDecision.Next(it)
              } ?: TransitionDecision.Skip
        }

        if (resultTransitionResult !is TransitionResult.Done) {
          onIllegalState(
              "decide transition result after os-native started (success=$startedSuccess)",
          )
        }
        val resultState = resultTransitionResult.current as VoiceDraftUiState.ActiveRecording
        // TODO: fill this table!
        when (resultState.action) {
          ActiveRecordingAction.CancelOnRace -> {
            launchCancelRecording(resultState, policy = RecordingCancelPolicy.DiscardAll).join()
          }
          ActiveRecordingAction.CancelOnStartFailure -> {}

          ActiveRecordingAction.Stop -> TODO()
          ActiveRecordingAction.StopOnRace -> TODO()
          ActiveRecordingAction.StopOnStartFailure -> TODO()
          ActiveRecordingAction.RollbackOnStartFailure,
          ActiveRecordingAction.StartOsNative,
          ActiveRecordingAction.Cancel,
          ActiveRecordingAction.Recording -> {}
        }

        startRecordingExecutor(recordingFilePath, sessionToken)
            .onFailure { e ->
              // rollback
              val currentState = _draftState.value
              if (currentState.isOnStopRace()) {
                // only rollback the last segment
                val nextState = currentState.rollbackOnFailure(draftUseCase)
                _draftState.update { nextState }
              } else if (currentState.isOnCancelRace()) {
                // NOTE: when in cancel race, we should discard all and always back to Idle.
                val nextState = currentState.discardAll(draftUseCase)
                _draftState.update { nextState }
              } else if (currentState is VoiceDraftUiState.ActiveRecording) {
                // Normal
                val nextState = currentState.rollbackOnFailure(draftUseCase)
                _draftState.update { nextState }
                onShowSnackbar(startFailureTipsProvider(e))
              } else {
                onIllegalState("rollback on os-native start fail")
              }
            }
            .onSuccess {
              val stateAfterStarted = _draftState.value
              if (stateAfterStarted.isOnStopRace()) {
                // even it's stop, logically we know the audio must be broken.
                // But in case it success if we call `Stop`, here we call Cancel directly.
                // Then we have to provides the next state because of the unmatches
                // Tips have to be shown at state-set point, as here we can't get osEventSession
                // info.
                launchCancelRecording(
                        stateAfterStarted,
                        policy = RecordingCancelPolicy.RollbackCurrentSegment,
                    )
                    .join()
              } else if (stateAfterStarted.isOnCancelRace()) {
                launchCancelRecording(
                        stateAfterStarted,
                        policy = RecordingCancelPolicy.DiscardAll,
                    )
                    .join()
              } else {
                // no cancel/stop race anymore, let's do normal process
                // osNative start success, Set [pendingAction] to null
                // Why use `tryTransition instead of directly update?
                // -- HoldToLock action can change the draft state from Holding to Locked!
                // -- Even we are still in the starting duration.
                // -- And it's intended.
                // -- Here use tryTransition can adapt to the condition.
                val onSuccessTransitionResult = _draftState.tryTransition { current ->
                  when (current) {
                    is VoiceDraftUiState.ActiveRecording -> {
                      TransitionDecision.Next(current.withMeta(action = null))
                    }
                    else -> TransitionDecision.Skip
                  }
                }
                if (onSuccessTransitionResult !is TransitionResult.Done) {
                  onIllegalState("clean pendingAction after os-native started")
                }
              }
            }
      } catch (e: Throwable) {
        Timber.w(e, "Failed when process coordinator started result, rollback")
        // NOTE: it can only be [Idle] / [CommittedDraft]. So no matter whether holding-to-locked
        // occurred,
        // set the rollbackState as trnasition.previous is safe.
        _draftState.update { transition.previous }
        throw e
      }
    }
  }

  @OptIn(ExperimentalAtomicApi::class)
  fun onStartHolding() {

    startActiveRecordingOrRollback(
        "hold",
        canStartRecordingFrom = { current -> current is VoiceDraftUiState.Idle },
        nextStateProvider = { userId, _ ->
          val draft = draftUseCase.newVoiceDraft(userId)
          VoiceDraftUiState.ActiveRecording.Holding(
              draft = draft,
              sessionToken = Any(),
              action = ActiveRecordingAction.StartOsNative,
          )
        },
        startRecordingExecutor = { destinationPath, sessionToken ->
          coordinator.start(
              destinationPath = destinationPath,
              onOsEvent = { event ->
                val osEventTrigger = RecordingOsEventSession(event, sessionToken)
                // When callback, hold may have changed to Locked.
                when (val currentState = _draftState.value) {
                  is VoiceDraftUiState.ActiveRecording.Holding -> {
                    onHoldStop(osEventTrigger)
                  }
                  is VoiceDraftUiState.ActiveRecording.Locked -> {
                    onPauseLockedRecording(osEventTrigger)
                  }
                  is VoiceDraftUiState.CommittedDraft,
                  VoiceDraftUiState.Idle -> {
                    Timber.w(
                        "When OsEvent [$osEventTrigger] occurs, dratState is invalid: $currentState",
                    )
                  }
                }
              },
          )
        },
        startFailureTipsProvider = { e ->
          "${context.getString(R.string.composer_hold_to_record_failed_tip)}: ${e.localizedMessage}"
        },
    )
  }

  private fun tryTransitCancel(
      osEventTrigger: RecordingOsEventSession?,
      onTransitedRaceAction: ((currentState: VoiceDraftUiState.ActiveRecording) -> Unit)? = null,
  ) = tryTransitCancelOrStop(osEventTrigger, true, onTransitedRaceAction = onTransitedRaceAction)

  private fun tryTransitStop(
      osEventTrigger: RecordingOsEventSession?,
      onTransitedRaceAction: ((currentState: VoiceDraftUiState.ActiveRecording) -> Unit)? = null,
  ) =
      tryTransitCancelOrStop(
          osEventTrigger,
          false,
          onTransitedRaceAction = onTransitedRaceAction,
      )

  private fun tryTransitCancelOrStop(
      osEventTrigger: RecordingOsEventSession?,
      isCancel: Boolean,
      onTransitedRaceAction: ((currentState: VoiceDraftUiState.ActiveRecording) -> Unit)? = null,
  ): VoiceDraftUiState.ActiveRecording? {
    val (raceAction, action) =
        when (isCancel) {
          true -> ActiveRecordingAction.CancelOnRace to ActiveRecordingAction.Cancel
          false -> ActiveRecordingAction.StopOnRace to ActiveRecordingAction.Stop
        }
    val transitionResult = _draftState.tryTransition { current ->
      if (
          // 没有必要再具体区分子类是 ActiveRecording.Hold or Locked. 因为
          // 1. UI 入口同一时刻只可能有 1 种 active recording mode
          // 2. 回调入口后面有 sessionToken 区分
          current is VoiceDraftUiState.ActiveRecording &&
              // 避免串台
              (osEventTrigger == null || osEventTrigger.sessionToken === current.sessionToken)
      ) {
        // Race condition: 底层没启动
        if (current.action == ActiveRecordingAction.StartOsNative) {
          return@tryTransition TransitionDecision.Next(current.withMeta(action = raceAction))
        } else if (current.action == null) {
          return@tryTransition TransitionDecision.Next(current.withMeta(action = action))
        }
      }
      return@tryTransition TransitionDecision.Skip
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.d("Failed to transit to neither $raceAction nor $action ")
      return null
    }
    val currentState = transitionResult.current as VoiceDraftUiState.ActiveRecording
    if (currentState.action == raceAction) {
      onTransitedRaceAction?.invoke(currentState)
      return null
    }
    return currentState
  }

  fun onHoldToLockGesture() {
    val transitionResult = _draftState.tryTransition { current ->
      // No mater if it's waiting for the coordinator initializing the recorder, draftState can
      // always
      // transfer to the Locked State.
      // After transfer, the `onHoldCancel` and `onHoldRelease` will do nothing as state guard!
      // It meets the requirement.
      if (current is VoiceDraftUiState.ActiveRecording.Holding) {
        val couldTransit =
            when (current.action) {
              ActiveRecordingAction.StartOsNative,
              null -> true
              ActiveRecordingAction.CancelOnRace,
              ActiveRecordingAction.Cancel,
              ActiveRecordingAction.Stop,
              ActiveRecordingAction.StopOnRace -> false
            }
        if (couldTransit) {
          val nextState =
              VoiceDraftUiState.ActiveRecording.Locked(
                  action = current.action,
                  draft = current.draft,
                  sessionToken = current.sessionToken,
              )
          return@tryTransition TransitionDecision.Next(nextState)
        }
      }
      return@tryTransition TransitionDecision.Skip
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.d("holdToLock was blocked on state = ${_draftState.value}")
      return
    }
    // NOTE: it's a sync fun
    coordinator.guarding(RecordingPrompt.Default)
  }

  /**
   * Use [appScope] to make sure it can always be canceled.
   *
   * When entering this Cancel step, we assume _draftState will be frozen so we'll directly update
   * the state.
   */
  private fun launchCancelRecording(
      state: VoiceDraftUiState.ActiveRecording,
      policy: RecordingCancelPolicy,
  ) = appScope.launchCatching {
    // `Try` to handle CancellationException.
    try {
      val onFailure =
          when (state.action) {
            ActiveRecordingAction.StartOsNative -> TODO()
            ActiveRecordingAction.CancelOnRace -> TODO()
            ActiveRecordingAction.CancelOnStartFailure -> TODO()
            ActiveRecordingAction.Cancel -> TODO()
            ActiveRecordingAction.Stop -> TODO()
            ActiveRecordingAction.StopOnRace -> TODO()
            ActiveRecordingAction.StopOnStartFailure -> TODO()
            ActiveRecordingAction.RollbackOnStartFailure -> TODO()
            null -> TODO()
          }.onFailure { e ->
            Timber.w(e, "Failed to cancel coordinator on state = $state")
          }
    } finally {
      // 用 NonCancellable 确保即使协程被取消，文件清理和状态复位也不会漏掉
      withContext(NonCancellable) {
        val nextState =
            when (policy) {
              RecordingCancelPolicy.DiscardAll -> state.discardAll(draftUseCase)
              RecordingCancelPolicy.RollbackCurrentSegment -> state.rollbackOnFailure(draftUseCase)
            }
        _draftState.update { nextState }
      }
    }
  }

  fun onHoldCancel() {
    val currentState = tryTransitCancel(null) ?: return
    launchCancelRecording(currentState, policy = RecordingCancelPolicy.DiscardAll)
  }

  /**
   * A general stop function supports Stop both Locked and Hold
   *
   * Once entering this, we assume the `_draftState` can't be updated anymore due to Guarding! So no
   * more check and directly update!
   *
   * Split action & state-provider in param to force caller providing the next state!
   */
  private fun launchStopRecording(
      currentState: VoiceDraftUiState.ActiveRecording,
      onFailureNextStateProvider: (committedDraft: VoiceDraft.Committed?) -> VoiceDraftUiState,
      onStopFailure:
          (suspend (
              committedDraft: VoiceDraft.Committed?,
              recordError: AudioRecordError?,
          ) -> Unit)?,
      onSuccessNextStateProvider: (committedDraft: VoiceDraft.Committed) -> VoiceDraftUiState,
      onStopSuccess: (suspend (committedDraft: VoiceDraft.Committed) -> Unit)?,
  ) = appScope.launchCatching {
    val draft = currentState.draft
    try {
      if (currentState is VoiceDraftUiState.ActiveRecording.Locked) {
            coordinator.onStopLocked()
          } else {
            coordinator.stop()
          }
          .onFailure { e ->
            Timber.w(e, "StopRecording failed, possibly AudioRecorder internal got Invalid State")
            // tryCommitWhenRecordingFailure will delete file!
            val committedDraft = draftUseCase.tryCommitWhenRecordingFailure(draft)
            // It can only happen when internal audioRecorder state invalid. use InternalError
            // Duration-Too-Short error won't report here, it'll return a normal
            // AudioRecordResult.Failure!
            onStopFailure?.invoke(committedDraft, null)
            val nextState = onFailureNextStateProvider(committedDraft)
            _draftState.update { nextState }
          }
          .onSuccess { audioRecordResult ->
            when (audioRecordResult) {
              is AudioRecordResult.Failure -> {
                val committedDraft = draftUseCase.tryCommitWhenRecordingFailure(draft = draft)
                onStopFailure?.invoke(committedDraft, audioRecordResult.error)
                val nextState = onFailureNextStateProvider(committedDraft)
                _draftState.update { nextState }
              }
              is AudioRecordResult.Success -> {
                val committedDraft =
                    draftUseCase.commitWhenRecordingSuccess(
                        draft = draft,
                        audioRecordResult = audioRecordResult,
                    )
                onStopSuccess?.invoke(committedDraft)
                val nextState = onSuccessNextStateProvider(committedDraft)
                _draftState.update { nextState }
              }
            }
          }
    } catch (e: Throwable) {
      // [draftUseCase.tryCommitWhenRecordingFailure] won't throw any exception
      val committedDraft = draftUseCase.tryCommitWhenRecordingFailure(draft)
      onStopFailure?.invoke(committedDraft, null)
      val nextState = onFailureNextStateProvider(committedDraft)
      _draftState.update { nextState }
      // do transite before throw e
      if (e is CancellationException) throw e
    }
  }

  fun onHoldStop() = onHoldStop(null)

  private fun onHoldStop(osEventTrigger: RecordingOsEventSession?) {
    val currentState =
        tryTransitStop(osEventTrigger) { state ->
          // here we must show tips as there are no tips when actual-stop(cancel)
          val couldRollback = state.isActiveRecordingLockedAppending()
          showTipsWhenStopError(
              recordError = AudioRecordError.TooShort, // We explicitly set the too-short error
              osEventTrigger = osEventTrigger,
              couldRollback = couldRollback,
          )
        } ?: return
    launchStopRecording(
        currentState = currentState,
        onFailureNextStateProvider = { VoiceDraftUiState.Idle },
        onStopFailure = { _, recordError ->
          showTipsWhenStopError(recordError, osEventTrigger, couldRollback = false)
        },
        onSuccessNextStateProvider = { VoiceDraftUiState.Idle },
        onStopSuccess = { committedDraft ->
          sendCommittedDraft(committedDraft)
          val tipsResId =
              when (osEventTrigger?.event) {
                RecordingOsEvent.FocusLost ->
                    R.string.composer_recording_error_focus_lost_stop_success
                RecordingOsEvent.OsNativeError ->
                    R.string.composer_recording_error_os_native_error_stop_success
                RecordingOsEvent.StorageFull ->
                    R.string.composer_recording_error_storage_full_stop_success
                null -> null
              }
          if (tipsResId != null) onShowSnackbar(context.getString(tipsResId))
        },
    )
  }

  private fun showTipsWhenStopError(
      recordError: AudioRecordError?,
      osEventTrigger: RecordingOsEventSession?,
      couldRollback: Boolean,
  ) {
    /** We trust [recordError] more as it's summarized on all knowledge */
    // we should first show osEventTrigger reason as it's from non-user intention.

    val storageFullResId = R.string.composer_recording_stop_fail_reason_storage_full
    val reasonResId =
        when (osEventTrigger?.event) {
          RecordingOsEvent.FocusLost -> R.string.composer_recording_stop_fail_reason_focus_lost
          RecordingOsEvent.OsNativeError -> {
            // But as we know, `RecordingOsEvent.OsNativeError` maybe not accurate enough
            // -- it can be StorageFull, so in this condition, we make an extra double-check
            if (recordError is AudioRecordError.StorageFull) storageFullResId
            else R.string.composer_recording_stop_fail_reason_os_native_error
          }
          RecordingOsEvent.StorageFull -> storageFullResId
          null ->
              when (recordError) {
                AudioRecordError.FileIo -> R.string.composer_recording_stop_fail_reason_file_io
                AudioRecordError.MicrophoneBusy ->
                    R.string.composer_recording_stop_fail_reason_microphone_busy
                AudioRecordError.PermissionDenied ->
                    R.string.composer_recording_stop_fail_reason_permission_denied
                AudioRecordError.StorageFull -> storageFullResId
                AudioRecordError.TooShort -> R.string.composer_recording_stop_fail_reason_too_short
                AudioRecordError.RecorderInternal,
                null -> R.string.composer_recording_stop_fail_reason_internal_error
              }
        }

    val resultResId =
        if (couldRollback) R.string.composer_recording_stop_fail_result_rollback
        else R.string.composer_recording_stop_fail_result_cannot_save
    val result = context.getString(resultResId)
    val message = context.getString(reasonResId, result)
    onShowSnackbar(message)
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

  private suspend fun mergeChunkIntoDraftInternal(
      userId: UserId,
      recordingChunkResult: AudioRecordResult,
  ): VoiceDraft? {
    val base = _draftState.value
    return if (base == null) {
      when (recordingChunkResult) {
        is AudioRecordResult.Failure -> {
          storageRepository.deleteFile(recordingChunkResult.filePath)
          null
        }
        is AudioRecordResult.Success -> {
          VoiceDraft(
              baseFilename = recordingChunkResult.filePath.fullRelativePath.last(),
              baseDurationMs = recordingChunkResult.duration.inWholeMilliseconds,
              baseWaveformBase64 =
                  WaveformCodec5Bit.encode(
                          recordingChunkResult.amplitudes,
                          recordingChunkResult.duration.inWholeSeconds,
                      )
                      .serializeToString(),
              pendingChunkFilename = null,
          )
        }
      }
    } else {
      when (recordingChunkResult) {
        is AudioRecordResult.Failure -> {
          storageRepository.deleteFile(recordingChunkResult.filePath)
          base.copy(pendingChunkFilename = null)
        }
        is AudioRecordResult.Success -> {
          // the file has already been checked. Just append to base file.
          val baseFilePath = restoreDraftPath(userId, base.baseFilename)
          val isSuccess =
              storageRepository.fileAppendContent(baseFilePath, recordingChunkResult.filePath)
          if (!isSuccess) {
            Timber.w(
                "Failed to append new audio recording chunking to base. " +
                    "${recordingChunkResult.filePath} -> $baseFilePath",
            )
          }
          storageRepository.deleteFile(recordingChunkResult.filePath)
          val mergedDurationMs =
              base.baseDurationMs + recordingChunkResult.duration.inWholeMilliseconds
          val mergedWaveformCompressedBytes =
              WaveformCodec5Bit.appendSegment(
                  oldBytes = base.baseWaveformBase64.deserializeToByteArray(),
                  oldDurationSeconds = base.duration.inWholeSeconds,
                  newRawAmplitudes = recordingChunkResult.amplitudes,
                  newDurationSeconds = recordingChunkResult.duration.inWholeSeconds,
              )
          base.copy(
              baseDurationMs = mergedDurationMs,
              baseWaveformBase64 = mergedWaveformCompressedBytes.serializeToString(),
              pendingChunkFilename = null,
          )
        }
      }
    }
  }

  private suspend fun sendActiveRecordingDraft(draft: VoiceDraft, result: AudioRecordResult) {
    val bars = WaveformCodec5Bit.calculateTargetBars(result.duration.inWholeSeconds)
    val compressed = WaveformCodec5Bit.encode(result.waveform, bars)
    sendVoiceMessageUseCase(result.filePath, result.duration, compressed, topicId, userId)
  }

  /**
   * NO exception will be thrown!
   *
   * Main-thread safe
   */
  private suspend fun sendCommittedDraft(draft: VoiceDraft.Committed) {}
}
