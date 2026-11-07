package top.fseasy.imlog.features.home.topiclog.composer2

import android.content.Context
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.data.util.appendContent
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.recorder.AudioRecordResult
import top.fseasy.imlog.domain.recorder.RecordingPrompt
import top.fseasy.imlog.domain.recorder.RecordingSessionState
import top.fseasy.imlog.domain.recorder.VoiceRecordingCoordinator
import top.fseasy.imlog.domain.recorder.WaveformCodec5Bit
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.SendVoiceMessageUseCase
import top.fseasy.imlog.domain.util.runSuspendCatching

// 宏观模式：只保留结构性数据，低频：只有切模式时才发射
sealed interface VoiceComposerMode {
  data object Idle : VoiceComposerMode

  data object Holding : VoiceComposerMode

  data class ActiveRecording(
      val baseWaveform: FloatArray?, // 历史底稿波形，静态数据
  ) : VoiceComposerMode {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as ActiveRecording

      return baseWaveform.contentEquals(other.baseWaveform)
    }

    override fun hashCode(): Int {
      return baseWaveform.contentHashCode()
    }
  }

  data class Draft(
      val draft: VoiceDraft,
      val isPlaying: Boolean = false,
  ) : VoiceComposerMode
}

class VoiceComposerStateHolder(
    private val topicId: TopicId,
    private val authState: StateFlow<AuthState>,
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    private val sendVoiceMessageUseCase: SendVoiceMessageUseCase,
    private val coordinator: VoiceRecordingCoordinator,
    private val onShowSnackbar: (String) -> Unit,
    private val scope: CoroutineScope, // 由 ComposerViewModel 的 viewModelScope 注入
    private val context: Context,
) : AutoCloseable {

  private val _draftState = MutableStateFlow<VoiceDraft?>(null)
  val draftState: StateFlow<VoiceDraft?> = _draftState.asStateFlow()

  val composerMode: StateFlow<VoiceComposerMode> =
      combine(coordinator.sessionState, _draftState) { session, draft ->
            when (session) {
              is RecordingSessionState.Locked ->
                  VoiceComposerMode.ActiveRecording(
                      baseWaveform = draft?.waveform ?: floatArrayOf()
                  )
              is RecordingSessionState.Holding -> VoiceComposerMode.Holding
              is RecordingSessionState.Idle if draft != null -> VoiceComposerMode.Draft(draft)
              else -> VoiceComposerMode.Idle
            }
          }
          .distinctUntilChanged()
          .stateIn(
              scope,
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
            val base = draft?.duration ?: Duration.ZERO
            base + snapshot.duration
          }
          .map { it.inWholeSeconds } // 只精确到秒，一秒只发射一次！
          .distinctUntilChanged()
          .map { it.seconds }

  fun restoreDraft(draft: VoiceDraft) {
    launchWithUserId { userId ->
      val baseFilePath = restoreDraftPath(userId, filename = draft.filename)
      // First check if base audio file is OK! if not, discard the draft
      if (
          !storageRepository.fileExists(baseFilePath) ||
              (storageRepository.fileSizeOrNull(baseFilePath) ?: 0) <= 0
      ) {
        Timber.w(
            "Restore draft get invalid base audio: ${draft.filename}, not exists or empty. Discard"
        )
        return@launchWithUserId
      }

      var safeDraft = draft

      // 检查是否有上次崩溃遗留的未合并分片
      if (!draft.pendingChunkFilename.isNullOrBlank()) {
        val pendingFilePath = restoreDraftPath(userId, filename = draft.pendingChunkFilename)

        if (
            storageRepository.fileExists(pendingFilePath) &&
                (storageRepository.fileSizeOrNull(pendingFilePath) ?: 0) > 0
        ) {
          Timber.w("Detected unfinished recording chunk from crash, recovering...")
          // 奇迹救援：把断电前录的声音拼回 baseFile
          val isSuccess = storageRepository.fileAppendContent(baseFilePath, pendingFilePath)
          if (!isSuccess) {
            Timber.w("Failed to append pending audio to base audio. Have to give up")
          }
        }
        storageRepository.deleteFile(pendingFilePath)
        // 修复完成，把 pendingChunkPath 擦除还原为干净的草稿
        safeDraft = draft.copy(pendingChunkFilename = null)
      }

      // 回归主线程，安全发布恢复后的草稿
      _draftState.update { safeDraft }
    }
  }

  fun onNewVoiceNote() {
    launchWithUserId { userId ->
      // Just in case: in general flow, current draft should be null in this condition
      clearDraftInternal(false)
      val path = newVoiceDraftPath(userId)
      coordinator.onDirectLock(RecordingPrompt.Default, path).onFailure { e ->
        onShowSnackbar(
            "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
        )
        // Delete file just in case
        storageRepository.deleteFile(path)
      }
    }
  }

  /** “继续录音” 绝不把 draftState 置 null，而是打上 pending 事务标记写入 DB！ */
  fun onResumeRecordingFromDraft() {
    val currentBase = draftState.value ?: return

    launchWithUserId { userId ->
      // 生成新的分片临时文件
      val newChunkPath = newVoiceDraftPath(userId)

      // 核心步骤 A：先写“事务日志”到 DB！
      // 记录我们正在录制 newChunkPath，底稿绝不丢失
      val transactionDraft =
          currentBase.copy(pendingChunkFilename = newChunkPath.fullRelativePath.last())
      _draftState.update { transactionDraft } // 这会触发 ViewModel 的 combine 立即安全持久化到 DB！
      val prompt = RecordingPrompt.Default
      // 核心步骤 B：启动录音引擎
      coordinator.onDirectLock(prompt, destinationPath = newChunkPath).onFailure { e ->
        onShowSnackbar(
            "${context.getString(R.string.composer_voice_note_start_failed_tip)}: ${e.localizedMessage}"
        )
        storageRepository.deleteFile(newChunkPath)
      }
    }
  }

  private fun newVoiceDraftPath(
      userId: UserId,
      now: Instant = Clock.System.now(),
  ) =
      sendVoiceMessageUseCase.newVoiceDraftPath(
          userId,
          messageTimestamp = now,
          audioFileExtension = coordinator.outputAudioFileExtension,
      )

  private fun restoreDraftPath(userId: UserId, filename: String) =
      sendVoiceMessageUseCase.restoreDraftPath(userId = userId, filename = filename)

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
    scope.launch {
      // NOTE! currently we have to delete the old one except implement multiple drafts!
      clearDraftInternal(false)
      if (!coordinator.onHoldToLockGesture(RecordingPrompt.Default)) {
        onShowSnackbar(context.getString(R.string.composer_hold_to_record_lock_failed_tip))
      }
    }
  }

  fun onHoldCancel() {
    scope.launch(Dispatchers.IO) {
      coordinator.onHoldCancel()?.let { storageRepository.deleteFile(it.filePath) }
    }
  }

  fun onHoldRelease() {
    scope.launch(Dispatchers.IO) {
      coordinator.onHoldRelease()?.let { sendRawChunkInternal(it) }
    }
  }

  /** 点击【发送】（无论是正在录音中点发送，还是在草稿态点发送） */
  fun onSendLockedOrDraft() {
    scope.launch(Dispatchers.IO) {
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

  suspend fun stopVoiceNote(): VoiceDraft? = runSuspendCatching {
    val chunkResult = coordinator.onStopLocked() ?: return@runSuspendCatching null
    mergeChunkIntoDraftInternal(chunkResult)
  }
      .getOrNull()

  fun discard() {
    scope.launch(Dispatchers.IO) {
      coordinator.onCancelLocked()
      clearDraftInternal(keepDraftFile = false)
    }
  }

  private fun mergeChunkIntoDraftInternal(chunkResult: AudioRecordResult): VoiceDraft {
    val base = _draftState.value
    val newChunkFile = File(chunkResult.filePath)

    val finalFile: File
    val totalDuration: Duration
    val totalWaveform: List<Float>

    if (base != null) {
      val baseFile = File(base.filePath)
      // 物理追加 AAC_ADTS 流
      baseFile.appendContent(newChunkFile)
      newChunkFile.delete()

      finalFile = baseFile
      totalDuration = base.duration + chunkResult.duration
      totalWaveform = base.waveform.toList() + chunkResult.waveform
    } else {
      finalFile = newChunkFile
      totalDuration = chunkResult.duration
      totalWaveform = chunkResult.waveform
    }

    val targetBars = WaveformCodec5Bit.calculateTargetBars(totalDuration.inWholeSeconds)
    val compressedWaveform = WaveformCodec5Bit.encode(totalWaveform, targetBars)

    // 事务提交：生成移除了 pendingChunkPath 标记的最终草稿
    val committedDraft =
        VoiceDraft.create(
            filePath = finalFile.absolutePath,
            duration = totalDuration,
            waveformBytes = compressedWaveform,
        )

    _draftState.value = committedDraft
    return committedDraft
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

  override fun close() {
    pauseOrExit()
  }

  private fun launchWithUserId(
      context: CoroutineDispatcher = Dispatchers.IO,
      block: suspend (userId: UserId) -> Unit,
  ) {
    val userId = (authState.value as? AuthState.Authenticated)?.userId ?: return
    scope.launch(context) {
      block(userId)
    }
  }
}
