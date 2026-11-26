package top.fseasy.imlog.domain.usecase.composer

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import top.fseasy.imlog.domain.audio.AppendMergeResult
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.CommittedAudioCheckResult
import top.fseasy.imlog.domain.audio.MergeAudioRecordingResult
import top.fseasy.imlog.domain.audio.MergeFailureReason
import top.fseasy.imlog.domain.audio.PendingAudioRecoveryResult
import top.fseasy.imlog.domain.audio.VoiceDraftAudioProcessor
import top.fseasy.imlog.domain.audio.Waveform
import top.fseasy.imlog.domain.model.LoadedVoiceDraft
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.model.VoiceDraftDetail
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.usecase.composer.CommitOnRecordingSuccessResult.Success
import top.fseasy.imlog.domain.usecase.pathpolicy.VoiceDraftPathUseCase

data class PreparedVoiceDraft(
    val draft: VoiceDraft.Committed?, // 绝对干净：voice 要么 null，要么 Committed
    val recoveryNotice: DraftRecoveryNotice? = null,
)

sealed interface DraftRecoveryNotice {
  val quarantinedFilePath: StoragePathModel.Shared?

  /** 追加片段损坏或不兼容，已保留之前的 base */
  data class AppendingSegmentDiscarded(
      override val quarantinedFilePath: StoragePathModel.Shared?,
  ) : DraftRecoveryNotice

  /** base 损坏或丢失，但保留了新录制的追加片段 */
  data class BaseSegmentLostKeptPending(
      override val quarantinedFilePath: StoragePathModel.Shared?,
  ) : DraftRecoveryNotice

  /** 整个语音草稿无法恢复（均已损坏/丢失，或 JSON 损坏） */
  data class VoiceDraftCorrupted(
      override val quarantinedFilePath: StoragePathModel.Shared?,
  ) : DraftRecoveryNotice
}

data class CommitDraftOnRecordingFailureResult(
    val draft: VoiceDraft.Committed?,
    val filesToDelete: List<StoragePathModel.Internal>,
)

sealed interface CommitOnRecordingSuccessResult {
  data class Success(
      val draft: VoiceDraft.Committed,
      val filesToDelete: List<StoragePathModel.Internal>,
  ) : CommitOnRecordingSuccessResult

  data class Failure(
      val reason: MergeFailureReason,
  ) : CommitOnRecordingSuccessResult
}

/** NOT auto-inject yet. we can uniform it under KMP */
class VoiceDraftUseCase(
    private val storageRepository: StorageRepository,
    private val topicRepository: TopicRepository,
    private val draftPathUseCase: VoiceDraftPathUseCase,
    private val outputAudioFileExtension: String,
    private val audioProcessor: VoiceDraftAudioProcessor,
) {

  fun newVoiceDraftPath(userId: UserId, now: Instant = Clock.System.now()) =
      draftPathUseCase.newVoiceDraftPath(
          userId,
          messageTimestamp = now,
          audioFileExtension = outputAudioFileExtension,
      )

  /**
   * following Write Ahead Log rule: just recording the path to the draft.
   *
   * No IO operations. No Exception will be thrown.
   */
  fun createBaseVoiceDraft(
      userId: UserId,
      now: Instant = Clock.System.now(),
  ): VoiceDraft.ActiveRecording.Base {
    val path = newVoiceDraftPath(userId, now)
    return VoiceDraft.ActiveRecording.Base(path)
  }

  fun createAppendingVoiceDraft(
      baseDraft: VoiceDraft.Committed,
      userId: UserId,
      now: Instant = Clock.System.now(),
  ): VoiceDraft.ActiveRecording.Appending {
    val path = newVoiceDraftPath(userId, now)
    return VoiceDraft.ActiveRecording.Appending(base = baseDraft.detail, recordingFilePath = path)
  }

  /**
   * safe for main-thread.
   *
   * No exception.
   */
  suspend fun commitOnRecordingSuccess(
      userId: UserId,
      draft: VoiceDraft.ActiveRecording,
      audioRecordResult: AudioRecordResult.Success,
  ): CommitOnRecordingSuccessResult =
      withContext(Dispatchers.IO + NonCancellable) {
        when (draft) {
          is VoiceDraft.ActiveRecording.Appending -> {
            val destPath = newVoiceDraftPath(userId)
            val result =
                audioProcessor.mergeAudioRecording(
                    base = draft.base,
                    recordingResult = audioRecordResult,
                    destPath = destPath,
                )
            when (result) {
              is MergeAudioRecordingResult.Success ->
                  Success(
                      draft =
                          VoiceDraft.Committed(
                              VoiceDraftDetail(
                                  filePath = destPath,
                                  duration = result.duration,
                                  waveform = result.waveform,
                              ),
                          ),
                      filesToDelete = listOf(draft.base.filePath, audioRecordResult.filePath),
                  )
              is MergeAudioRecordingResult.Failure ->
                  CommitOnRecordingSuccessResult.Failure(result.reason)
            }
          }
          is VoiceDraft.ActiveRecording.Base ->
              Success(
                  draft =
                      VoiceDraft.Committed(
                          VoiceDraftDetail(
                              audioRecordResult.filePath,
                              duration = audioRecordResult.duration,
                              waveform =
                                  Waveform.fromRawAmplitudes(
                                      audioRecordResult.amplitudes,
                                      audioRecordResult.duration,
                                  ),
                          ),
                      ),
                  filesToDelete = emptyList(),
              )
        }
      }

  /** delete all possible files. */
  suspend fun deleteDraftFiles(draft: VoiceDraft) =
      withContext(Dispatchers.IO + NonCancellable) {
        when (draft) {
          is VoiceDraft.ActiveRecording.Appending -> {
            storageRepository.deleteFile(draft.recordingFilePath)
            storageRepository.deleteFile(draft.base.filePath)
          }
          is VoiceDraft.ActiveRecording.Base ->
              storageRepository.deleteFile(draft.recordingFilePath)
          is VoiceDraft.Committed -> storageRepository.deleteFile(draft.detail.filePath)
        }
      }

  suspend fun saveDraftToDb(userId: UserId, topicId: TopicId, draft: VoiceDraft?): Boolean =
      topicRepository.setMessageDraftVoiceContent(userId, topicId = topicId, draft = draft)

  /** 进入聊天页面时调用： 读取、校验并自愈语音草稿，确保输出的 draft 要么为 null，要么是可直接播放/发送的 Committed。 */
  suspend fun prepareVoiceDraftOnEnter(
      userId: UserId,
      topicId: TopicId,
  ): PreparedVoiceDraft {
    return when (val loaded = topicRepository.getMessageVoiceDraft(userId, topicId)) {
      LoadedVoiceDraft.None -> PreparedVoiceDraft(draft = null)

      LoadedVoiceDraft.Corrupted.Json -> {
        saveDraftToDb(userId, topicId = topicId, draft = null)
        PreparedVoiceDraft(
            draft = null,
            recoveryNotice = DraftRecoveryNotice.VoiceDraftCorrupted(quarantinedFilePath = null),
        )
      }

      is LoadedVoiceDraft.Corrupted.WaveformUnpack -> {
        recoverFromWaveformUnpackFailure(userId, topicId, loaded)
      }

      is LoadedVoiceDraft.Valid -> {
        reconcileValidVoiceDraft(userId, topicId, loaded.draft)
      }
    }
  }

  /**
   * 仅 DB 里的 waveform5BitBase64 损坏，但文件名还在： 利用 recoverPending 重新扫描 base 音频文件并重建 Waveform，尽量做到无感自愈。
   */
  private suspend fun recoverFromWaveformUnpackFailure(
      userId: UserId,
      topicId: TopicId,
      corrupted: LoadedVoiceDraft.Corrupted.WaveformUnpack,
  ): PreparedVoiceDraft {
    return when (val baseRecover = audioProcessor.recoverPending(corrupted.baseFilePath)) {
      is PendingAudioRecoveryResult.Ready -> {
        val recoveredBase =
            VoiceDraftDetail(
                filePath = corrupted.baseFilePath,
                duration = baseRecover.duration,
                waveform = baseRecover.waveform,
            )
        val pendingPath = corrupted.recordingFilePath
        if (pendingPath == null) {
          // 原来就是 Committed：写回修复后的波形即可，静默自愈
          val committed = VoiceDraft.Committed(recoveredBase)
          saveDraftToDb(userId, topicId, committed)
          PreparedVoiceDraft(draft = committed)
        } else {
          // 原来正在 Appending：恢复出 base 后直接复用 Appending 合并流程
          reconcileAppending(
              userId = userId,
              topicId = topicId,
              appending =
                  VoiceDraft.ActiveRecording.Appending(
                      base = recoveredBase,
                      recordingFilePath = pendingPath,
                  ),
          )
        }
      }

      PendingAudioRecoveryResult.Missing,
      is PendingAudioRecoveryResult.Corrupted ->
          handleBaseLostOrCorrupted(
              userId = userId,
              topicId = topicId,
              baseFilePath = corrupted.baseFilePath,
              recordingFilePath = corrupted.recordingFilePath,
          )
    }
  }

  private suspend fun reconcileValidVoiceDraft(
      userId: UserId,
      topicId: TopicId,
      draft: VoiceDraft,
  ): PreparedVoiceDraft =
      when (draft) {
        is VoiceDraft.Committed -> reconcileCommitted(userId, topicId, draft)
        is VoiceDraft.ActiveRecording.Base -> reconcileActiveBase(userId, topicId, draft)
        is VoiceDraft.ActiveRecording.Appending -> reconcileAppending(userId, topicId, draft)
      }

  private suspend fun reconcileCommitted(
      userId: UserId,
      topicId: TopicId,
      committed: VoiceDraft.Committed,
  ): PreparedVoiceDraft {
    return when (audioProcessor.validateCommitted(committed.detail.filePath)) {
      CommittedAudioCheckResult.Valid -> PreparedVoiceDraft(draft = committed)

      CommittedAudioCheckResult.Missing,
      is CommittedAudioCheckResult.Corrupted -> {
        withContext(NonCancellable + Dispatchers.IO) {
          val quarantined = quarantineBrokenFile(committed.detail.filePath, userId)
          saveDraftToDb(userId, topicId, null)
          PreparedVoiceDraft(
              draft = null,
              recoveryNotice =
                  DraftRecoveryNotice.VoiceDraftCorrupted(quarantinedFilePath = quarantined),
          )
        }
      }
    }
  }

  private suspend fun reconcileActiveBase(
      userId: UserId,
      topicId: TopicId,
      activeBase: VoiceDraft.ActiveRecording.Base,
  ): PreparedVoiceDraft {
    return when (val recovery = audioProcessor.recoverPending(activeBase.recordingFilePath)) {
      is PendingAudioRecoveryResult.Ready -> {
        val committed =
            VoiceDraft.Committed(
                VoiceDraftDetail(
                    filePath = activeBase.recordingFilePath,
                    duration = recovery.duration,
                    waveform = recovery.waveform,
                ),
            )
        saveDraftToDb(userId, topicId, committed)
        PreparedVoiceDraft(draft = committed)
      }

      PendingAudioRecoveryResult.Missing,
      is PendingAudioRecoveryResult.Corrupted -> {
        withContext(NonCancellable + Dispatchers.IO) {
          val quarantined = quarantineBrokenFile(activeBase.recordingFilePath, userId)
          saveDraftToDb(userId, topicId, null)
          PreparedVoiceDraft(
              draft = null,
              recoveryNotice =
                  DraftRecoveryNotice.VoiceDraftCorrupted(quarantinedFilePath = quarantined),
          )
        }
      }
    }
  }

  private suspend fun reconcileAppending(
      userId: UserId,
      topicId: TopicId,
      appending: VoiceDraft.ActiveRecording.Appending,
  ): PreparedVoiceDraft {
    val mergedPath =
        draftPathUseCase.newVoiceDraftPath(
            userId,
            messageTimestamp = Clock.System.now(),
            audioFileExtension = outputAudioFileExtension,
        )

    return when (
        val mergeResult =
            audioProcessor.mergeWithRecovery(
                basePath = appending.base.filePath,
                baseDuration = appending.base.duration,
                baseWaveform = appending.base.waveform,
                pendingPath = appending.recordingFilePath,
                destPath = mergedPath,
            )
    ) {
      is AppendMergeResult.Merged -> {
        val committed =
            VoiceDraft.Committed(
                VoiceDraftDetail(
                    filePath = mergedPath,
                    duration = mergeResult.duration,
                    waveform = mergeResult.waveform,
                ),
            )
        // 先更新 DB 指向新合并的文件，再删除旧源片段，确保崩溃安全
        withContext(Dispatchers.IO + NonCancellable) {
          saveDraftToDb(userId, topicId, committed)
          storageRepository.deleteFile(appending.base.filePath)
          storageRepository.deleteFile(appending.recordingFilePath)
        }
        PreparedVoiceDraft(draft = committed)
      }

      is AppendMergeResult.PendingUnrecoverableKeepBase,
      AppendMergeResult.OutputPathInvalid,
      AppendMergeResult.IncompatibleFormat -> {
        // 保住原本健康的 base 并升格为 Committed
        val committed = VoiceDraft.Committed(appending.base)
        val quarantinedPending =
            withContext(NonCancellable + Dispatchers.IO) {
              saveDraftToDb(userId, topicId, committed)
              quarantineBrokenFile(appending.recordingFilePath, userId)
            }
        PreparedVoiceDraft(
            draft = committed,
            recoveryNotice = DraftRecoveryNotice.AppendingSegmentDiscarded(quarantinedPending),
        )
      }

      is AppendMergeResult.BaseCorrupted ->
          handleBaseLostOrCorrupted(
              userId = userId,
              topicId = topicId,
              baseFilePath = appending.base.filePath,
              recordingFilePath = appending.recordingFilePath,
          )
    }
  }

  /** 当 base 确认丢失或损坏时调用： 若存在合法的 [recordingFilePath]，则将其升格为新的 Committed；否则彻底清理草稿。 */
  private suspend fun handleBaseLostOrCorrupted(
      userId: UserId,
      topicId: TopicId,
      baseFilePath: StoragePathModel.Internal,
      recordingFilePath: StoragePathModel.Internal?,
  ): PreparedVoiceDraft {
    val pendingRecovery = recordingFilePath?.let { audioProcessor.recoverPending(it) }

    return withContext(NonCancellable) {
      val quarantinedBase = quarantineBrokenFile(baseFilePath, userId)

      if (recordingFilePath != null && pendingRecovery is PendingAudioRecoveryResult.Ready) {
        val committed =
            VoiceDraft.Committed(
                VoiceDraftDetail(
                    filePath = recordingFilePath,
                    duration = pendingRecovery.duration,
                    waveform = pendingRecovery.waveform,
                ),
            )
        saveDraftToDb(userId, topicId, committed)
        PreparedVoiceDraft(
            draft = committed,
            recoveryNotice = DraftRecoveryNotice.BaseSegmentLostKeptPending(quarantinedBase),
        )
      } else {
        val quarantinedPending = recordingFilePath?.let { quarantineBrokenFile(it, userId) }
        saveDraftToDb(userId, topicId, null)
        PreparedVoiceDraft(
            draft = null,
            recoveryNotice =
                DraftRecoveryNotice.VoiceDraftCorrupted(
                    quarantinedFilePath = quarantinedBase ?: quarantinedPending,
                ),
        )
      }
    }
  }

  private suspend fun quarantineBrokenFile(
      brokenPath: StoragePathModel.Internal,
      userId: UserId,
  ): StoragePathModel.Shared? {
    // currently we keep the same name
    val filename = VoiceDraftPathUseCase.getRestorableDraftFilename(brokenPath)
    val quarantinePath = draftPathUseCase.newQuarantinePath(userId, filename)
    val isQuarantined = audioProcessor.quarantineOrDelete(brokenPath, quarantinePath)
    return if (isQuarantined) quarantinePath else null
  }

  companion object {
    /**
     * 1. if it's Base => Null
     * 2. If it's appending, put the base back to committed.
     *
     * Won't delete files.
     *
     * No exception will be thrown.
     *
     * Safe for main-thread.
     */
    fun commitByRollbackOnRecordingFailure(
        draft: VoiceDraft.ActiveRecording,
    ): CommitDraftOnRecordingFailureResult {
      val rollbackDraft =
          when (draft) {
            is VoiceDraft.ActiveRecording.Appending -> VoiceDraft.Committed(draft.base)
            is VoiceDraft.ActiveRecording.Base -> null
          }
      return CommitDraftOnRecordingFailureResult(
          draft = rollbackDraft,
          filesToDelete = listOf(draft.recordingFilePath),
      )
    }
  }
}
