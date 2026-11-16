package top.fseasy.imlog.features.home.topiclog.composer2

import android.content.Context
import androidx.compose.runtime.Immutable
import java.io.File
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.common.TransitionDecision
import top.fseasy.imlog.common.TransitionResult
import top.fseasy.imlog.common.launchCatching
import top.fseasy.imlog.common.runSuspendCatching
import top.fseasy.imlog.common.tryTransition
import top.fseasy.imlog.data.audio.AndroidAudioWaveformExtractor
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.RecordingPrompt
import top.fseasy.imlog.domain.audio.RecordingSessionState
import top.fseasy.imlog.domain.audio.VoiceRecordingCoordinator
import top.fseasy.imlog.domain.audio.Waveform
import top.fseasy.imlog.domain.audio.WaveformCodec5Bit
import top.fseasy.imlog.domain.audio.deserializeToByteArray
import top.fseasy.imlog.domain.audio.serializeToString
import top.fseasy.imlog.domain.model.AuthState
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

@Immutable
data class VoicePlayerUiState(
    val durationText: String,
    val waveform: Waveform, // 已经适配当前设备宽度的柱状高度列表
    val playbackProgress: Float,
    val isPlaying: Boolean = false,
)

// 宏观模式：只保留结构性数据，低频：只有切模式时才发射
sealed interface VoiceComposerMode {
  data object Idle : VoiceComposerMode

  data object Holding : VoiceComposerMode

  data class ActiveRecording(
      val baseWaveform: Waveform?, // 历史底稿波形，静态数据
  ) : VoiceComposerMode

  data class Draft(val playerUiState: VoicePlayerUiState) : VoiceComposerMode
}

@Immutable
sealed interface VoiceDraftUiState {

  @Immutable data object Idle : VoiceDraftUiState

  @Immutable
  sealed interface ActiveRecording : VoiceDraftUiState {
    val waitingCoordinator: Boolean
    val draft: VoiceDraft.ActiveRecording

    @Immutable
    data class Holding(
        override val waitingCoordinator: Boolean,
        override val draft: VoiceDraft.ActiveRecording.Base,
    ) : ActiveRecording

    @Immutable
    data class Locked(
        override val waitingCoordinator: Boolean,
        override val draft: VoiceDraft.ActiveRecording,
    ) : ActiveRecording
  }

  @Immutable data class CommittedDraft(val draft: VoiceDraft.Committed) : VoiceDraftUiState
}

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

  private val _draftState = MutableStateFlow<VoiceDraftUiState>(VoiceDraftUiState.Idle())

  /**
   * 快按快松竞态 PressReleaseRace 处理：
   * - 用户按下后立刻松开（不论是 Cancel/Release） 此时 onHoldStart 内部的 recorder 还没启动，根本没有录音
   * - 因为 State 的保护，不能走正常的 clean 流程，所以设置 Signal, 让 clean 在 holdStart 中做
   */
  @OptIn(ExperimentalAtomicApi::class) private var pendingHoldCancel: AtomicBoolean? = null

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
                      )
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

  fun onNewVoiceNote() {
    // Idle -> Locked
    val userId = authState.value.authenticatedIdOrNull() ?: return
    val draft = draftUseCase.newVoiceDraft(userId)
    val nextState =
        VoiceDraftUiState.ActiveRecording.Locked(waitingCoordinator = true, draft = draft)
    val transition = tryAcquireIdleStartLock(nextState, "NewVoiceNote") ?: return

    viewModelScope.launchCatching {
      try {
        coordinator
            .onDirectLock(RecordingPrompt.Default, draft.recordingFilePath)
            .onFailure { e ->
              storageRepository.deleteFile(draft.recordingFilePath)
              _draftState.update { transition.previous }
              onShowSnackbar(
                  "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
              )
            }
            .onSuccess {
              _draftState.update { nextState.copy(waitingCoordinator = false) }
            }
      } finally {
        restoreStateIfTransitionFailed(transition)
      }
    }
  }

  fun onResumeRecording() {
    val userId = authState.value.authenticatedIdOrNull() ?: return
    // Guard
    val stateTransitionResult = _draftState.tryTransition { current ->
      if (current is VoiceDraftUiState.CommittedDraft) {
        // Just build object in Memory, no side effects
        val resumeDraft = draftUseCase.appending(baseDraft = current.draft, userId = userId)
        TransitionDecision.Next(
            VoiceDraftUiState.ActiveRecording.Locked(waitingCoordinator = true, draft = resumeDraft)
        )
      } else {
        TransitionDecision.Skip
      }
    }
    if (stateTransitionResult !is TransitionResult.Done) {
      Timber.w("Invalid state: Call ResumeRecording while on state = ${_draftState.value}")
      return
    }
    val currentState = stateTransitionResult.current as VoiceDraftUiState.ActiveRecording.Locked
    val pendingPath = currentState.draft.recordingFilePath

    viewModelScope.launchCatching {
      val prompt = RecordingPrompt.Default
      try {
        coordinator
            .onDirectLock(prompt, destinationPath = pendingPath)
            .onFailure { e ->
              storageRepository.deleteFile(pendingPath)
              // rollback state
              _draftState.update { stateTransitionResult.previous }
              onShowSnackbar(
                  "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
              )
            }
            .onSuccess {
              _draftState.update { currentState.copy(waitingCoordinator = false) }
            }
      } finally {
        restoreStateIfTransitionFailed(stateTransitionResult)
      }
    }
  }

  @OptIn(ExperimentalAtomicApi::class)
  fun onHoldStart() {
    val userId = authState.value.authenticatedIdOrNull() ?: return
    val draft = draftUseCase.newVoiceDraft(userId)
    val nextState =
        VoiceDraftUiState.ActiveRecording.Holding(waitingCoordinator = true, draft = draft)
    val transition = tryAcquireIdleStartLock(nextState, "HoldStart") ?: return
    // 确保只有抢锁成功后，才绑定本次按住的取消信号
    val cancelSignal = AtomicBoolean(false)
    pendingHoldCancel = cancelSignal

    viewModelScope.launchCatching {
      try {
        coordinator
            .onHoldDown(draft.recordingFilePath)
            .onFailure { e ->
              storageRepository.deleteFile(draft.recordingFilePath)
              _draftState.update { transition.previous }
              // Show only when user hadn't canceled it.
              if (!cancelSignal.load()) {
                onShowSnackbar(
                    "${context.getString(R.string.composer_hold_to_record_failed_tip)}: ${e.localizedMessage}"
                )
              }
            }
            .onSuccess {
              // User already release hold. go cancel logic as lower level Recorder haven't
              // recording anything.
              if (cancelSignal.load()) {
                coordinator.onHoldCancel()
                storageRepository.deleteFile(draft.recordingFilePath)
                _draftState.update { transition.previous }
              } else {
                // To new state.
                // NOTE: consider the HoldToLock gesture action, our lock can't prevent its
                // updating. I.e., _draftState can be changed to Locked when go back here.
                val stateAfterCoordinatorDone = _draftState.value
                val updatedState =
                    if (stateAfterCoordinatorDone is VoiceDraftUiState.ActiveRecording.Locked) {
                      stateAfterCoordinatorDone.copy(waitingCoordinator = false)
                    } else {
                      // It should be impossible. Let's assert it
                      require(stateAfterCoordinatorDone == nextState) {
                        val msg =
                            "Unknow state: onHoldStart get unexpected draft state: " +
                                "$stateAfterCoordinatorDone, have to hard reset"
                        Timber.e(msg)
                        msg
                      }
                      nextState.copy(waitingCoordinator = false)
                    }
                _draftState.update { updatedState }
              }
            }
      } finally {
        // Only reset when it haven't been changed in other parts (currently can't happen)
        if (pendingHoldCancel == cancelSignal) {
          pendingHoldCancel = null
        }
        restoreStateIfTransitionFailed(transition)
      }
    }
  }

  private fun tryAcquireIdleStartLock(
      nextState: VoiceDraftUiState,
      actionName: String,
  ): TransitionResult.Done<VoiceDraftUiState>? {
    val result = _draftState.tryTransition { current ->
      if (current is VoiceDraftUiState.Idle) {
        TransitionDecision.Next(nextState)
      } else {
        TransitionDecision.Skip
      }
    }
    if (result !is TransitionResult.Done) {
      Timber.w("Invalid state: call $actionName while state = ${_draftState.value}")
      return null
    }
    return result
  }

  private fun restoreStateIfTransitionFailed(transition: TransitionResult.Done<VoiceDraftUiState>) {
    _draftState.tryTransition { current ->
      if (current == transition.current) {
        TransitionDecision.Next(transition.previous)
      } else {
        TransitionDecision.Skip
      }
    }
  }

  /**
   * 快按快松竞态 PressReleaseRace 时发送信号取消
   *
   * @return if signaled.
   */
  @OptIn(ExperimentalAtomicApi::class)
  private inline fun <
      reified T : VoiceDraftUiState.ActiveRecording
  > signalCancelOnPressReleaseRace(): Boolean {
    val currentState = _draftState.value
    if (currentState is T && currentState.waitingCoordinator) {
      pendingHoldCancel?.store(true)
      return true
    }
    return false
  }

  fun onHoldToLockGesture() {
    val transitionResult = _draftState.tryTransition { current ->
      // No mater if it's waiting for the coordinator initializing the recorder, draftState can
      // always
      // transfer to the Locked State.
      // After transfer, the `onHoldCancel` and `onHoldRelease` will do nothing as state guard!
      // It meets the requirement.
      if (current is VoiceDraftUiState.ActiveRecording.Holding) {
        val nextState =
            VoiceDraftUiState.ActiveRecording.Locked(
                waitingCoordinator = current.waitingCoordinator,
                draft = current.draft,
            )
        TransitionDecision.Next(nextState)
      } else {
        TransitionDecision.Skip
      }
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.w("Invalid State: holdToLock on state = ${_draftState.value}")
      return
    }
    // NOTE: it's a sync fun
    coordinator.onHoldToLockGesture(RecordingPrompt.Default)
  }

  @OptIn(ExperimentalAtomicApi::class)
  fun onHoldCancel() {
    if (signalCancelOnPressReleaseRace<VoiceDraftUiState.ActiveRecording.Holding>()) return
    val transitionResult = _draftState.tryTransition { current ->
      if (current is VoiceDraftUiState.ActiveRecording.Holding && !current.waitingCoordinator) {
        TransitionDecision.Next(VoiceDraftUiState.Idle)
      } else {
        TransitionDecision.Skip
      }
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.w("Invalid state: call HoldCancel on state = ${_draftState.value}")
      return
    }
    viewModelScope.launchCatching {
      coordinator
          .onHoldCancel()
          .onFailure { e ->
            Timber.w(e, "Failed to Cancel Hold")
          }
          .onSuccess { cancelResult ->
            storageRepository.deleteFile(cancelResult.filePath)
          }
    }
  }

  fun onHoldRelease() {
    // Release too fast.
    if (signalCancelOnPressReleaseRace<VoiceDraftUiState.ActiveRecording.Holding>()) {
      onShowSnackbar(context.getString(R.string.composer_hold_to_record_voice_duration_too_short))
      return
    }
    val transitionResult = _draftState.tryTransition { current ->
      if (current is VoiceDraftUiState.ActiveRecording.Holding && !current.waitingCoordinator) {
        TransitionDecision.Next(VoiceDraftUiState.Idle)
      } else {
        TransitionDecision.Skip
      }
    }
    if (transitionResult !is TransitionResult.Done) {
      Timber.w("Invalid state: call HoldCancel on state = ${_draftState.value}")
      return
    }
    // Use AppScope to make sure result can be saved.
    appScope.launchCatching {
      coordinator
          .onHoldRelease()
          .onFailure { e -> Timber.w(e) }
          .onSuccess { recordResult ->
            val draft =
                (transitionResult.previous as VoiceDraftUiState.ActiveRecording.Holding).draft
            sendActiveRecordingDraft(draft, recordResult)
          }
    }
  }

  /** 点击【发送】（无论是正在录音中点发送，还是在草稿态点发送） */
  fun onSendLockedOrDraft() {
    viewModelScope.launch(Dispatchers.IO) {
      val chunkResult = coordinator.onStopLocked()
      val finalDraft =
          if (chunkResult != null) {
            mergeChunkIntoDraftInternal(chunkResult)
          } else {
            _draftState.value
          }

      finalDraft?.let {
        sendDraftInternal(it)
        clearDraftInternal(keepDraftFile = true) // SendUsecase will delete it after copying done
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
        coordinator.onHoldCancel()?.let { storageRepository.deleteFile(it.filePath) }
        null
      }
      VoiceComposerMode.Idle -> TODO()
    }
  }

  suspend fun stopVoiceNote(): VoiceDraft? = runSuspendCatching {
    val chunkResult = coordinator.onStopLocked() ?: return@runSuspendCatching null
    mergeChunkIntoDraftInternal(chunkResult)
  }
      .getOrNull()

  fun discard() {
    viewModelScope.launch(Dispatchers.IO) {
      coordinator.onCancelLocked()?.let { storageRepository.deleteFile(it.filePath) }
      clearDraftInternal(keepDraftFile = false)
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
                          recordingChunkResult.waveform,
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
                    "${recordingChunkResult.filePath} -> $baseFilePath"
            )
          }
          storageRepository.deleteFile(recordingChunkResult.filePath)
          val mergedDurationMs =
              base.baseDurationMs + recordingChunkResult.duration.inWholeMilliseconds
          val mergedWaveformCompressedBytes =
              WaveformCodec5Bit.appendSegment(
                  oldBytes = base.baseWaveformBase64.deserializeToByteArray(),
                  oldDurationSeconds = base.duration.inWholeSeconds,
                  newRawAmplitudes = recordingChunkResult.waveform,
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

  private suspend fun sendDraftInternal(draft: VoiceDraft) {
    sendVoiceMessageUseCase(
        draft.filePath,
        draft.duration,
        draft.getWaveformBytes(),
        topicId,
        userId,
    )
  }

  private fun clearDraftInternal(keepDraftFile: Boolean) {
    _draftState.value?.let {
      if (!keepDraftFile) File(it.filePath).delete()
      it.pendingChunkPath?.let { chunk -> File(chunk).delete() }
    }
    _draftState.update { null }
  }

  private fun launchWithUserId(
      context: CoroutineDispatcher = Dispatchers.IO,
      block: suspend (userId: UserId) -> Unit,
  ) {
    val userId = (authState.value as? AuthState.Authenticated)?.userId ?: return
    viewModelScope.launch(context) {
      block(userId)
    }
  }
}
