package top.fseasy.imlog.features.home.topiclog.composer2

import android.content.Context
import androidx.compose.runtime.Immutable
import java.io.File
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
import top.fseasy.imlog.domain.util.runSuspendCatching
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
  val isUpdating: Boolean

  @Immutable data class Idle(override val isUpdating: Boolean = false) : VoiceDraftUiState

  @Immutable
  data class Active(override val isUpdating: Boolean, val draft: VoiceDraft) : VoiceDraftUiState
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
    val userId = authState.value.authenticatedIdOrNull() ?: return
    // State guard
    val stateTransitResult = _draftState.tryTransition { current ->
      val couldTransit = current is VoiceDraftUiState.Idle && !current.isUpdating
      if (!couldTransit) {
        return@tryTransition TransitionDecision.Skip
      }
      TransitionDecision.Next(VoiceDraftUiState.Idle(isUpdating = true))
    }
    if (stateTransitResult !is TransitionResult.Done) {
      Timber.w("Invalid State: Call NewVoiceNote while it's not idle, or it's already in updating")
      return
    }

    val newDraft = draftUseCase.newVoiceDraft(userId) // No exception here
    viewModelScope.launch {
      try {
        coordinator
            .onDirectLock(RecordingPrompt.Default, newDraft.path)
            .onFailure { e ->
              _draftState.update { VoiceDraftUiState.Idle(false) }
              // Delete file just in case
              storageRepository.deleteFile(newDraft.path)
              onShowSnackbar(
                  "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
              )
            }
            .onSuccess {
              _draftState.update {
                VoiceDraftUiState.Active(
                    isUpdating = false,
                    draft = newDraft,
                )
              }
            }
      } finally {
        // restore state if unexpected exceptions occur in previous `onFailure/onSuccess`.
        _draftState.tryTransition { current ->
          if (current is VoiceDraftUiState.Idle && current.isUpdating) {
            TransitionDecision.Next(VoiceDraftUiState.Idle(isUpdating = false))
          } else {
            TransitionDecision.Skip
          }
        }
      }
    }
  }

  fun onResumeRecording() {
    val userId = authState.value.authenticatedIdOrNull() ?: return
    // Guard
    val stateTransitionResult = _draftState.tryTransition { current ->
      if (
          !current.isUpdating &&
              current is VoiceDraftUiState.Active &&
              current.draft is VoiceDraft.Committed
      ) {
        TransitionDecision.Next(current.copy(isUpdating = true))
      } else {
        TransitionDecision.Skip
      }
    }
    if (stateTransitionResult !is TransitionResult.Done) {
      Timber.w("Invalid state: Call ResumeRecording while on state = ${_draftState.value}")
      return
    }
    val lockingState = stateTransitionResult.current as VoiceDraftUiState.Active
    val baseDraft = lockingState.draft as VoiceDraft.Committed
    // Just create a memory object, no side effects
    val resumeDraft = draftUseCase.resumeVoiceDraft(baseDraft = baseDraft, userId = userId)
    val pendingPath = resumeDraft.pendingChunkFilePath

    viewModelScope.launch {
      val prompt = RecordingPrompt.Default
      try {
        coordinator.onDirectLock(prompt, destinationPath = pendingPath).onFailure { e ->
          // rollback state
          _draftState.update { stateTransitionResult.previous }
          storageRepository.deleteFile(pendingPath)
          onShowSnackbar(
              "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
          )
        }.onSuccess {
          _draftState.update { VoiceDraftUiState.Active(isUpdating = false, draft = resumeDraft) }
        }
      }
      finally{
        _draftState.tryTransition { state ->
          // rollback if it's still in the guard entering state
          if (state == lockingState) {
            TransitionDecision.Next(stateTransitionResult.previous)
          } else {
            TransitionDecision.Skip
          }
        }
      }
    }
  }

  // --- 手势动作 ---
  fun onHoldStart() {
    launchWithUserId { userId ->
      val path = newVoiceDraftPath(userId)
      coordinator.onHoldDown(path).onFailure { e ->
        onShowSnackbar(
            "${context.getString(R.string.composer_hold_to_record_failed_tip)}: ${e.localizedMessage}"
        )
        storageRepository.deleteFile(path)
      }
    }
  }

  fun onHoldToLockGesture() {
    // NO io operation
    viewModelScope.launch {
      // NOTE! currently we have to delete the old one except implement multiple drafts!
      clearDraftInternal(false)
      if (!coordinator.onHoldToLockGesture(RecordingPrompt.Default)) {
        onShowSnackbar(context.getString(R.string.composer_hold_to_record_lock_failed_tip))
      }
    }
  }

  fun onHoldCancel() {
    viewModelScope.launch(Dispatchers.IO) {
      coordinator.onHoldCancel()?.let { storageRepository.deleteFile(it.filePath) }
    }
  }

  fun onHoldRelease() {
    viewModelScope.launch(Dispatchers.IO) {
      coordinator.onHoldRelease()?.let { sendRawChunkInternal(it) }
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

  private suspend fun sendRawChunkInternal(result: AudioRecordResult) {
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
