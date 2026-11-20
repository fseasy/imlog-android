package top.fseasy.imlog.features.home.topiclog.composer2.voice

import android.content.Context
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
      coordinator.onDirectLock(
          prompt = RecordingPrompt.Default,
          destinationPath = destinationPath,
          onOsEvent = { event ->
            onPauseLockedRecording(RecordingOsEventSession(event, sessionToken))
          },
      )

  fun onDirectLock() {
    startActiveRecordingOrRollback(
        sourceActionName = "DirectLock",
        canStartRecordingFrom = { current -> current is VoiceDraftUiState.Idle },
        nextStateProvider = { userId, _ ->
          val draft = draftUseCase.newVoiceDraft(userId)
          VoiceDraftUiState.ActiveRecording.Locked(
              draft = draft,
              sessionToken = Any(),
              pendingAction = ActiveRecordingPendingAction.StartOsNative,
          )
        },
        startRecordingExecutor = ::executeDirectLockRecording,
        startFailureTipsProvider = { e ->
          "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
        },
    )
  }

  fun onPauseLockedRecording() = onPauseLockedRecording(null)

  private fun onPauseLockedRecording(osEventTrigger: RecordingOsEventSession?) {
    fun updateStateOnCommittedDraft(committedDraft: VoiceDraft.Committed?) {
      if (committedDraft != null) {
        _draftState.update { VoiceDraftUiState.CommittedDraft(draft = committedDraft) }
      } else {
        _draftState.update { VoiceDraftUiState.Idle }
      }
    }
    if (pendingCancelRecordingIfWaitingCoordinator(osEventTrigger)) {
      val tipsResId =
          when (osEventTrigger?.event) {
            RecordingOsEvent.FocusLost -> R.string.composer_recording_error_focus_lost_cancel
            RecordingOsEvent.OsNativeError ->
                R.string.composer_recording_error_os_native_error_cancel
            RecordingOsEvent.StorageFull -> R.string.composer_recording_error_storage_full_cancel
            null -> null
          }
      if (tipsResId != null) onShowSnackbar(context.getString(tipsResId))
      return
    }
    // Guard: 1. recorder must be working! 2. session must match if it's from OsEvent callback
    val stateTransitionResult = _draftState.tryTransition { current ->
      if (
          current is VoiceDraftUiState.ActiveRecording.Locked &&
              current.pendingAction == null &&
              (osEventTrigger == null || current.sessionToken === osEventTrigger.sessionToken)
      ) {
        TransitionDecision.Next(current.copy(pendingAction = ActiveRecordingPendingAction.Stop))
      } else {
        TransitionDecision.Skip
      }
    }
    if (stateTransitionResult !is TransitionResult.Done) {
      Timber.w("Invalid state: Call PauseLocked while on state = ${_draftState.value}")
      return
    }
    appScope.launchCatching {
      val lockedState = stateTransitionResult.previous as VoiceDraftUiState.ActiveRecording.Locked
      val draft = lockedState.draft
      try {
        coordinator
            .onStopLocked()
            .onFailure { e ->
              Timber.w(e, "StopLocked failed, should be AudioRecorder internal get Invalid State")
              val committedDraft = draftUseCase.tryCommitWhenRecordingFailure(draft)
              updateStateOnCommittedDraft(committedDraft)
              // It can only happen when internal audioRecorder state invalid. use InternalError
              // Duration-Too-Short error won't report here, it'll return a normal
              // AudioRecordResult.Failure!
              onShowSnackbar(context.getString(R.string.internal_error_head))
            }
            .onSuccess { audioRecordResult ->
              val committedDraft =
                  when (audioRecordResult) {
                    is AudioRecordResult.Failure -> {
                      draftUseCase.tryCommitWhenRecordingFailure(draft = draft)
                    }
                    is AudioRecordResult.Success -> {
                      draftUseCase.commitWhenRecordingSuccess(
                          draft = draft,
                          audioRecordResult = audioRecordResult,
                      )
                    }
                  }
              updateStateOnCommittedDraft(committedDraft)
              // tips
              val isRecordingSuccess = audioRecordResult is AudioRecordResult.Success
              val tipResId =
                  if (isRecordingSuccess) {
                    when (osEventTrigger?.event) {
                      RecordingOsEvent.FocusLost ->
                          R.string.composer_recording_error_focus_lost_stop_success
                      RecordingOsEvent.OsNativeError ->
                          R.string.composer_recording_error_os_native_error_stop_success
                      RecordingOsEvent.StorageFull ->
                          R.string.composer_recording_error_storage_full_stop_success
                      null -> null
                    }
                  } else {
                    when (osEventTrigger?.event) {
                      RecordingOsEvent.FocusLost ->
                          R.string.composer_recording_error_focus_lost_stop_fail
                      RecordingOsEvent.OsNativeError ->
                          R.string.composer_recording_error_os_native_error_stop_fail
                      RecordingOsEvent.StorageFull ->
                          R.string.composer_recording_error_storage_full_stop_fail
                      null -> R.string.composer_voice_note_stop_fail
                    }
                  }
              if (tipResId != null) onShowSnackbar(context.getString(tipResId))
            }
      } catch (e: Throwable) {
        val committedDraft = draftUseCase.tryCommitWhenRecordingFailure(draft)
        updateStateOnCommittedDraft(committedDraft)
        throw e
      }
    }
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
              pendingAction = ActiveRecordingPendingAction.StartOsNative,
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

    val currentActiveRecordingState =
        tryTransitStop(
            osEventTrigger = null,
        ) {
          onShowSnackbar(
              context.getString(R.string.composer_recording_error_voice_duration_too_short),
          )
        } ?: return ifInCommittedDraftState()
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
    appScope.launchCatching {
      try {
        when (val recordingState = transitionResult.previous) {
          is VoiceDraftUiState.ActiveRecording.Locked -> {
            coordinator
                .onStopLocked()
                .onFailure { e ->
                  Timber.w(
                      e,
                      "StopLocked failed, should be AudioRecorder internal get Invalid State",
                  )
                  // It can only happen when internal audioRecorder state invalid. use InternalError
                  // Duration-Too-Short error won't report here, it'll return a normal
                  // AudioRecordResult.Failure!
                  onShowSnackbar(context.getString(R.string.internal_error_head))
                }
                .onSuccess { audioRecordResult ->
                  sendActiveRecordingDraft(recordingState.draft, audioRecordResult)
                  // tips
                  if (audioRecordResult is AudioRecordResult.Failure) {
                    onShowSnackbar(context.getString(R.string.composer_voice_note_stop_fail))
                  }
                }
          }
          is VoiceDraftUiState.CommittedDraft -> {
            sendCommittedDraft(recordingState.draft)
          }
          else -> throw IllegalStateException("Do Send Draft on state = $recordingState")
        }
      } finally {
        // always reset to Idle
        _draftState.update { VoiceDraftUiState.Idle }
      }
    }
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
    val transitedState = transition.current as VoiceDraftUiState.ActiveRecording
    val sessionToken = transitedState.sessionToken
    val recordingFilePath = transitedState.draft.recordingFilePath

    fun currentHasCancelBeforeStart() =
        (_draftState.value as? VoiceDraftUiState.ActiveRecording)?.pendingAction ==
            ActiveRecordingPendingAction.CancelOnRace

    fun onIllegalState(env: String): Nothing {
      val currentState = _draftState.value
      val msg = "$sourceActionName got IllegalState [$currentState] under $env"
      Timber.e(msg)
      throw IllegalStateException(msg)
    }

    viewModelScope.launch {
      try {
        startRecordingExecutor(recordingFilePath, sessionToken)
            .onFailure { e ->
              storageRepository.deleteFile(recordingFilePath)
              val hasCancel = currentHasCancelBeforeStart()
              // rollback to previous state
              _draftState.update { transition.previous }
              // Show only when user hadn't canceled it.
              if (!hasCancel) {
                onShowSnackbar(startFailureTipsProvider(e))
              }
            }
            .onSuccess {
              val hasCancel = currentHasCancelBeforeStart()
              // Check if user has canceled the recording.
              if (hasCancel) {
                val method =
                    (_draftState.value as? VoiceDraftUiState.ActiveRecording)?.method()
                        ?: onIllegalState("cancel after os-native started")
                launchCancelRecording(method).join() // wait until it finished.
              } else {
                // osNative start success, Set [pendingAction] to null
                // Why use `tryTransition instead of directly update?
                // -- HoldToLock action can change the draft state from Holding to Locked!
                // -- Even we are still in the starting duration.
                // -- And it's intended.
                // -- Here use tryTransition can adapt to the condition.

                val onSuccessTransitionResult = _draftState.tryTransition { current ->
                  when (current) {
                    is VoiceDraftUiState.ActiveRecording -> {
                      TransitionDecision.Next(current.withMeta(pendingAction = null))
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
        _draftState.update { transition.previous }
      }
    }
  }

  @OptIn(ExperimentalAtomicApi::class)
  fun onHoldStart() {
    startActiveRecordingOrRollback(
        "HoldStart",
        canStartRecordingFrom = { current -> current is VoiceDraftUiState.Idle },
        nextStateProvider = { userId, _ ->
          val draft = draftUseCase.newVoiceDraft(userId)
          VoiceDraftUiState.ActiveRecording.Holding(
              draft = draft,
              sessionToken = Any(),
              pendingAction = ActiveRecordingPendingAction.StartOsNative,
          )
        },
        startRecordingExecutor = { destinationPath, sessionToken ->
          coordinator.onHoldStart(
              destinationPath = destinationPath,
              onOsEvent = { event -> onHoldStop(RecordingOsEventSession(event, sessionToken)) },
          )
        },
        startFailureTipsProvider = { e ->
          "${context.getString(R.string.composer_hold_to_record_failed_tip)}: ${e.localizedMessage}"
        },
    )
  }

  private fun pendingCancelRecordingIfWaitingCoordinator(osEventTrigger: RecordingOsEventSession?) =
      tryTransitPendingActionIfWaitingOsNativeStart(
          osEventTrigger,
          ActiveRecordingPendingAction.CancelOnRace,
      )

  private fun pendingStopRecordingIfWaitingCoordinator(osEventTrigger: RecordingOsEventSession?) =
      tryTransitPendingActionIfWaitingOsNativeStart(
          osEventTrigger,
          ActiveRecordingPendingAction.StopOnRace,
      )

  /**
   * ActiveRecording 在遇到快按快松竞态时设置 pendingAction （此时不能直接调用 coordinator.cancel 因为底层还没返回，
   * 调用会导致资源泄露——底层并没有状态保护）
   *
   * @return if signaled. 只在第一次设置取消信号成功时返回 true. 也即如果已经 cancel 了，就不再设置并返回 false
   */
  private fun tryTransitPendingActionIfWaitingOsNativeStart(
      osEventTrigger: RecordingOsEventSession?,
      targetPendingAction: ActiveRecordingPendingAction,
  ): Boolean {
    val transitionResult = _draftState.tryTransition { current ->
      if (
          // 没有必要再具体区分子类是 ActiveRecording.Hold or Locked. 因为
          // 1. UI 入口同一时刻只可能有 1 种 active recording mode
          // 2. 回调入口后面有 sessionToken 区分
          current is VoiceDraftUiState.ActiveRecording &&
              // 底层没启动
              current.pendingAction == ActiveRecordingPendingAction.StartOsNative &&
              // 避免串台
              (osEventTrigger == null || osEventTrigger.sessionToken === current.sessionToken)
      ) {
        TransitionDecision.Next(current.withMeta(pendingAction = targetPendingAction))
      } else TransitionDecision.Skip
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.d("Failed to transit to pendingAction=$targetPendingAction when Pending StartOsNative")
      return false
    }
    return true
  }

  private fun tryTransitCancel(
      osEventTrigger: RecordingOsEventSession?,
      onTransitedRaceAction: (() -> Unit)? = null,
  ) = tryTransitCancelOrStop(osEventTrigger, true, onTransitedRaceAction = onTransitedRaceAction)

  private fun tryTransitStop(
      osEventTrigger: RecordingOsEventSession?,
      onTransitedRaceAction: (() -> Unit)? = null,
  ) =
      tryTransitCancelOrStop(
          osEventTrigger,
          false,
          onTransitedRaceAction = onTransitedRaceAction,
      )

  private fun tryTransitCancelOrStop(
      osEventTrigger: RecordingOsEventSession?,
      isCancel: Boolean,
      onTransitedRaceAction: (() -> Unit)? = null,
  ): VoiceDraftUiState.ActiveRecording? {
    val (raceAction, action) =
        when (isCancel) {
          true -> ActiveRecordingPendingAction.CancelOnRace to ActiveRecordingPendingAction.Cancel
          false -> ActiveRecordingPendingAction.StopOnRace to ActiveRecordingPendingAction.Stop
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
        if (current.pendingAction == ActiveRecordingPendingAction.StartOsNative) {
          TransitionDecision.Next(current.withMeta(pendingAction = raceAction))
        } else if (current.pendingAction == null) {
          TransitionDecision.Next(current.withMeta(pendingAction = action))
        }
      }
      TransitionDecision.Skip
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.d("Failed to transit to neither $raceAction nor $action ")
      return null
    }
    val currentState = transitionResult.current as VoiceDraftUiState.ActiveRecording
    if (currentState.pendingAction == raceAction && onTransitedRaceAction != null) {
      onTransitedRaceAction()
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
            when (current.pendingAction) {
              ActiveRecordingPendingAction.StartOsNative,
              null -> true
              ActiveRecordingPendingAction.CancelOnRace,
              ActiveRecordingPendingAction.Cancel,
              ActiveRecordingPendingAction.Stop,
              ActiveRecordingPendingAction.StopOnRace -> false
            }
        if (couldTransit) {
          val nextState =
              VoiceDraftUiState.ActiveRecording.Locked(
                  pendingAction = current.pendingAction,
                  draft = current.draft,
                  sessionToken = current.sessionToken,
              )
          TransitionDecision.Next(nextState)
        }
      }
      TransitionDecision.Skip
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.d("holdToLock was blocked on state = ${_draftState.value}")
      return
    }
    // NOTE: it's a sync fun
    coordinator.onTransformHoldToLock(RecordingPrompt.Default)
  }

  /** Use [appScope] to make sure it can always be canceled. */
  private fun launchCancelRecording(method: RecordingMethod) = appScope.launchCatching {
    try {
      val cancelResult =
          when (method) {
            RecordingMethod.Holding -> coordinator.onHoldCancel()
            RecordingMethod.Locked -> coordinator.onCancelLocked()
          }
      cancelResult
          .onFailure { e ->
            Timber.w(e, "Failed to Cancel Coordinator $method Recording")
          }
          .onSuccess { cancelResult ->
            storageRepository.deleteFile(cancelResult.filePath)
          }
    } finally {
      // We'll always set it to Idle no matter if cancel succeeded.
      _draftState.update { VoiceDraftUiState.Idle }
    }
  }

  @OptIn(ExperimentalAtomicApi::class)
  fun onHoldCancel() {
    val currentState = tryTransitCancel(null) ?: return
    launchCancelRecording(RecordingMethod.Holding)
  }

  fun onHoldStop() = onHoldStop(null)

  /**
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
    val stopResult =
        if (currentState is VoiceDraftUiState.ActiveRecording.Locked) {
          coordinator.onStopLocked()
        } else {
          coordinator.onHoldStop()
        }
    try {
      stopResult
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

  private fun onHoldStop(osEventTrigger: RecordingOsEventSession?) {
    val currentState =
        tryTransitStop(osEventTrigger) {
          val tipResId =
              when (osEventTrigger?.event) {
                RecordingOsEvent.FocusLost -> R.string.composer_recording_error_focus_lost_cancel
                RecordingOsEvent.OsNativeError ->
                    R.string.composer_recording_error_os_native_error_cancel
                RecordingOsEvent.StorageFull ->
                    R.string.composer_recording_error_storage_full_cancel
                null -> R.string.composer_recording_error_voice_duration_too_short
              }
          onShowSnackbar(context.getString(tipResId))
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

  /** We trust [recordError] more as it's summarized on all knowledge */
  private fun showTipsWhenStopError(
      recordError: AudioRecordError?,
      osEventTrigger: RecordingOsEventSession?,
      couldRollback: Boolean,
  ) {
    val reasonResId =
        when (recordError) {
          AudioRecordError.FileIo -> R.string.composer_recording_stop_fail_reason_file_io
          AudioRecordError.MicrophoneBusy ->
              R.string.composer_recording_stop_fail_reason_microphone_busy
          AudioRecordError.PermissionDenied ->
              R.string.composer_recording_stop_fail_reason_permission_denied
          AudioRecordError.StorageFull -> R.string.composer_recording_stop_fail_reason_storage_full
          AudioRecordError.TooShort -> R.string.composer_recording_stop_fail_reason_too_short
          AudioRecordError.RecorderInternal,
          null ->
              when (osEventTrigger?.event) {
                RecordingOsEvent.FocusLost ->
                    R.string.composer_recording_stop_fail_reason_focus_lost
                RecordingOsEvent.OsNativeError ->
                    R.string.composer_recording_stop_fail_reason_os_native_error
                RecordingOsEvent.StorageFull ->
                    R.string.composer_recording_stop_fail_reason_storage_full
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
        coordinator.onHoldCancel()?.let { storageRepository.deleteFile(it.filePath) }
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

  /** NO exception will be thrown!
   *
   * Main-thread safe
   * */
  private suspend fun sendCommittedDraft(draft: VoiceDraft.Committed) {}
}
