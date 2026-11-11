package top.fseasy.imlog.domain.usecase.composer

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.domain.audio.AudioWaveformExtractor
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.TopicRepository

/** NOT auto-inject yet. we can uniform it under KMP */
class VoiceDraftUseCase(
    private val storageRepository: StorageRepository,
    private val topicRepository: TopicRepository,
    private val draftPathUseCase: VoiceDraftPathUseCase,
    private val outputAudioFileExtension: String,
    private val audioWaveformExtractor: AudioWaveformExtractor,
) {

  /** following Write Ahead Log rule: just recording the path to the draft */
  fun newVoiceDraft(
      userId: UserId,
      now: Instant = Clock.System.now(),
  ): VoiceDraft {
    val path =
        draftPathUseCase.newVoiceDraftPath(
            userId,
            messageTimestamp = now,
            audioFileExtension = outputAudioFileExtension,
        )
    return VoiceDraft(base = null, pendingChunkFilePath = path)
  }

  private fun restoreDraftPath(userId: UserId, filename: String) =
    draftPathUseCase.restoreDraftPath(userId = userId, filename = filename)

  /** Load, verify(check & fix if necessary) voice draft */
  suspend fun loadValidDraft(userId: UserId, topicId: TopicId): VoiceDraft? {

    /** Return updated voice draft. Will delete the pending file. */
    suspend fun _tryToMergePendingAudioToBase(
        rawDraft: VoiceDraft,
        pendingFilePath: StoragePathModel,
    ): VoiceDraft? {

      if (rawDraft.pendingChunkFilename.isNullOrBlank()) return rawDraft

      var updatedDraft = rawDraft
      val pendingFilePath = restoreDraftPath(userId, filename = rawDraft.pendingChunkFilename)

      if (
          storageRepository.fileExists(pendingFilePath) &&
              (storageRepository.fileSizeOrNull(pendingFilePath) ?: 0) > 0
      ) {
        Timber.w("Detected unfinished recording chunk from crash, recovering...")
        // 奇迹救援：把断电前录的声音拼回 baseFile
        // 先获取waveform 和时长
        val extractResult = audioWaveformExtractor.extract5BitEncodedWaveformOrNull(pendingFilePath)
        if (extractResult == null) {
          Timber.w("Failed to parse pending audio file [$pendingFilePath]. Have to give up")
          return rawDraft
        }
        val isSuccess = storageRepository.fileAppendContent(baseFilePath, pendingFilePath)
        if (!isSuccess) {
          Timber.w("Failed to append pending audio to base audio. Have to give up")
          updatedDraft = updatedDraft.copy(pendingChunkFilename = null)
        } else {}
      }
      storageRepository.deleteFile(pendingFilePath)
      // 修复完成，把 pendingChunkPath 擦除还原为干净的草稿
      safeDraft = draft.copy(pendingChunkFilename = null)
    }

    suspend fun _fixDraftIfNecessary(rawDraft: VoiceDraft): VoiceDraft? {

      val baseFilePath = restoreDraftPath(userId, filename = rawDraft.baseFilename)
      // First check if base audio file is OK! if not, discard the draft
      if (
          !storageRepository.fileExists(baseFilePath) ||
              (storageRepository.fileSizeOrNull(baseFilePath) ?: 0) <= 0
      ) {
        Timber.w(
            "Restore draft get invalid base audio: ${rawDraft.baseFilename}, not exists or empty. Discard"
        )
        return null
      }
      // !NOTE!: We just check the base file path, don't check the duration / validity
      // AS the stability of ADTS format should be stronger than our media duration checker
      var updatedDraft = rawDraft
      // 检查是否有上次崩溃遗留的未合并分片
      if (!rawDraft.pendingChunkFilename.isNullOrBlank()) {
        val pendingFilePath = restoreDraftPath(userId, filename = rawDraft.pendingChunkFilename)

        if (
            storageRepository.fileExists(pendingFilePath) &&
                (storageRepository.fileSizeOrNull(pendingFilePath) ?: 0) > 0
        ) {
          Timber.w("Detected unfinished recording chunk from crash, recovering...")
          // 奇迹救援：把断电前录的声音拼回 baseFile
          // 先获取waveform 和时长
          val extractResult =
              audioWaveformExtractor.extract5BitEncodedWaveformOrNull(pendingFilePath)
          if (extractResult == null) {
            Timber.w("Failed to parse pending audio file [$pendingFilePath]. Have to give up")
          }
          val isSuccess = storageRepository.fileAppendContent(baseFilePath, pendingFilePath)
          if (!isSuccess) {
            Timber.w("Failed to append pending audio to base audio. Have to give up")
            updatedDraft = updatedDraft.copy(pendingChunkFilename = null)
          } else {}
        }
        storageRepository.deleteFile(pendingFilePath)
        // 修复完成，把 pendingChunkPath 擦除还原为干净的草稿
        safeDraft = draft.copy(pendingChunkFilename = null)
      }
    }

    return withContext(Dispatchers.IO) {
      val rawDraft =
          topicRepository
              .getMessageDraft(
                  userId = userId,
                  topicId = topicId,
              )
              ?.voiceNote ?: return@withContext null
    }
  }
}
