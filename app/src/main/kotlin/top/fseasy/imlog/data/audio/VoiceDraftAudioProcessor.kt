package top.fseasy.imlog.data.audio

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.data.commonmain.audio.AdtsDraftScanner
import top.fseasy.imlog.data.commonmain.audio.AdtsPendingScanResult
import top.fseasy.imlog.data.commonmain.audio.AdtsQuickCheckResult
import top.fseasy.imlog.data.mapper.toFile
import top.fseasy.imlog.domain.PathResolveException
import top.fseasy.imlog.domain.audio.AppendMergeResult
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.AudioWaveformExtractor
import top.fseasy.imlog.domain.audio.CommittedAudioCheckResult
import top.fseasy.imlog.domain.audio.MergeAudioRecordingResult
import top.fseasy.imlog.domain.audio.MergeFailureReason
import top.fseasy.imlog.domain.audio.PendingAudioRecoveryResult
import top.fseasy.imlog.domain.audio.VoiceDraftAudioProcessor
import top.fseasy.imlog.domain.audio.Waveform
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.VoiceDraftDetail
import top.fseasy.imlog.domain.repository.StorageRepository

class AdtsVoiceDraftAudioProcessor
@Inject
constructor(
    private val storageRepository: StorageRepository,
    private val waveformExtractor: AudioWaveformExtractor,
) : VoiceDraftAudioProcessor {

  /**
   * Main-thread safe
   *
   * @throws kotlinx.coroutines.CancellationException
   */
  override suspend fun validateCommitted(
      path: StoragePathModel.Internal,
  ): CommittedAudioCheckResult {
    val file =
        try {
          resolvePathOrThrow(path)
        } catch (_: PathResolveException) {
          return CommittedAudioCheckResult.Missing
        }
    return when (val result = AdtsDraftScanner.quickValidate(file)) {
      is AdtsQuickCheckResult.Valid -> CommittedAudioCheckResult.Valid
      AdtsQuickCheckResult.Missing -> CommittedAudioCheckResult.Missing
      is AdtsQuickCheckResult.Invalid -> CommittedAudioCheckResult.Corrupted(result.reason)
    }
  }

  /**
   * Main-thread safe
   *
   * @throws kotlinx.coroutines.CancellationException
   */
  override suspend fun recoverPending(
      path: StoragePathModel.Internal,
  ): PendingAudioRecoveryResult {
    val file =
        try {
          resolvePathOrThrow(path)
        } catch (_: PathResolveException) {
          return PendingAudioRecoveryResult.Missing
        }
    return when (val scan = AdtsDraftScanner.scanAndRepairPending(file, trimCorruptedTail = true)) {
      is AdtsPendingScanResult.Valid -> {
        val waveform = extractWaveform(path, scan.duration)
        PendingAudioRecoveryResult.Ready(duration = scan.duration, waveform = waveform)
      }
      AdtsPendingScanResult.Missing -> PendingAudioRecoveryResult.Missing
      is AdtsPendingScanResult.Corrupted -> PendingAudioRecoveryResult.Corrupted(scan.reason)
    }
  }

  /**
   * Main-thread safe
   *
   * @throws kotlinx.coroutines.CancellationException
   */
  override suspend fun mergeWithRecovery(
      basePath: StoragePathModel.Internal,
      baseWaveform: Waveform?,
      baseDuration: Duration,
      pendingPath: StoragePathModel.Internal,
      destPath: StoragePathModel.Internal,
  ): AppendMergeResult {
    val baseFile =
        try {
          resolvePathOrThrow(basePath)
        } catch (_: PathResolveException) {
          return AppendMergeResult.BaseCorrupted("resolve base path failed")
        }
    val baseCheck = AdtsDraftScanner.quickValidate(baseFile)
    if (baseCheck !is AdtsQuickCheckResult.Valid) {
      val reason = (baseCheck as? AdtsQuickCheckResult.Invalid)?.reason ?: "Base missing"
      return AppendMergeResult.BaseCorrupted(reason)
    }

    val pendingFile =
        try {
          resolvePathOrThrow(pendingPath)
        } catch (_: PathResolveException) {
          return AppendMergeResult.PendingUnrecoverableKeepBase("resolve pending file path filed.")
        }
    val pendingScan = AdtsDraftScanner.scanAndRepairPending(pendingFile, trimCorruptedTail = true)
    if (pendingScan !is AdtsPendingScanResult.Valid) {
      val reason = (pendingScan as? AdtsPendingScanResult.Corrupted)?.reason ?: "Pending missing"
      return AppendMergeResult.PendingUnrecoverableKeepBase(reason)
    }

    if (baseCheck.config != pendingScan.config) {
      return AppendMergeResult.IncompatibleFormat
    }

    val destFile =
        try {
          resolvePathOrThrow(destPath)
        } catch (_: PathResolveException) {
          return AppendMergeResult.OutputPathInvalid
        }
    val committed =
        mergeAndCommit(
            baseFile = baseFile,
            pendingFile = pendingFile,
            pendingValidByteLength = pendingScan.validByteLength,
            destFile = destFile,
        )
    if (!committed) {
      return AppendMergeResult.PendingUnrecoverableKeepBase("Merge or commit failed")
    }

    val newDuration = baseDuration + pendingScan.duration
    val newWaveform =
        if (baseWaveform != null && !baseWaveform.isEmpty && baseDuration > Duration.ZERO) {
          // 到这里时，base audio 肯定是非空的，所以这里也同意对 baseWaveform 做内容非空的判定
          // 只对已修复的短 pending 段抽取波形，再纯内存拼接
          val pendingWaveform = extractWaveform(pendingPath, pendingScan.duration)
          baseWaveform.appendWaveform(
              oldDuration = baseDuration,
              other = pendingWaveform,
              otherDuration = pendingScan.duration,
          )
        } else {
          extractWaveform(destPath, newDuration)
        }

    return AppendMergeResult.Merged(
        duration = newDuration,
        waveform = newWaveform,
    )
  }

  /**
   * Main-thread safe.
   *
   * @throws
   */
  override suspend fun mergeAudioRecording(
      base: VoiceDraftDetail,
      recordingResult: AudioRecordResult.Success,
      destPath: StoragePathModel.Internal,
  ): MergeAudioRecordingResult {
    val (baseFile, pendingFile, destFile) =
        try {
          listOf(base.filePath, recordingResult.filePath, destPath).map { resolvePathOrThrow(it) }
        } catch (e: PathResolveException) {
          Timber.w(e)
          return MergeAudioRecordingResult.Failure(MergeFailureReason.FilePathInvalid)
        }

    /** 当 pending 文件为空：将 base 音频复制到 dest, 复用 base 的 duration + waveform */
    fun handleEmptyAppending(): MergeAudioRecordingResult {
      if (!baseFile.exists() || baseFile.length() == 0L) {
        return MergeAudioRecordingResult.Failure(MergeFailureReason.BothFileMissingOrEmpty)
      }
      if (baseFile.canonicalFile != destFile.canonicalFile) {
        // copy base -> tmp, atomic move tmp to dest.
        // WHY not copy base -> dest? => to make sure dest will always be identity to the baseFile,
        // 避免复制到一半时 crash/强杀导致文件不完整； 整个流程效率一样，因为 atomic move 基本没开销
        val tempFile = createMergingTempFile(destFile)
        try {
          tempFile.parentFile?.mkdirs()
          baseFile.copyTo(tempFile, overwrite = true)
          if (!commitTempFile(tempFile, destFile)) {
            return MergeAudioRecordingResult.Failure(MergeFailureReason.FileIo)
          }
        } catch (e: Exception) {
          Timber.w(e, "Copy base to $destFile failed")
          tempFile.delete()
          return MergeAudioRecordingResult.Failure(MergeFailureReason.FileIo)
        }
      }
      return MergeAudioRecordingResult.Success(duration = base.duration, waveform = base.waveform)
    }

    return withContext(Dispatchers.IO + NonCancellable) {
      // 1) 快速处理空 appending（正常流程中短按秒停的常见情况）
      if (!pendingFile.exists() || pendingFile.length() == 0L) {
        return@withContext handleEmptyAppending()
      }

      // 2) O(1) 轻量检查（共读 ~28 bytes）：确保文件合法且采样率/声道一致
      val baseCheck = AdtsDraftScanner.quickValidate(baseFile)
      if (baseCheck !is AdtsQuickCheckResult.Valid) {
        val checkInvalidReason = (baseCheck as? AdtsQuickCheckResult.Invalid)?.reason
        if (checkInvalidReason != null) {
          Timber.w("ADTS check base found invalid: $checkInvalidReason")
        }
        val reason =
            if (checkInvalidReason != null) MergeFailureReason.BaseAudioContentCorrupted
            else MergeFailureReason.BaseFileMissingOrEmpty
        return@withContext MergeAudioRecordingResult.Failure(reason)
      }
      val pendingCheck = AdtsDraftScanner.quickValidate(pendingFile)
      if (pendingCheck !is AdtsQuickCheckResult.Valid) {
        val checkInvalidReason = (pendingCheck as? AdtsQuickCheckResult.Invalid)?.reason
        if (checkInvalidReason != null) {
          Timber.w("ADTS check pending found invalid: $checkInvalidReason")
        }
        return@withContext MergeAudioRecordingResult.Failure(
            MergeFailureReason.PendingAudioContentCorrupted,
        )
      }
      if (baseCheck.config != pendingCheck.config) {
        return@withContext MergeAudioRecordingResult.Failure(
            MergeFailureReason.IncompatibleFormat,
        )
      }

      // 3) 跳过全文件扫描与波形提取，直接合并并原子替换
      val committed =
          mergeAndCommit(
              baseFile = baseFile,
              pendingFile = pendingFile,
              pendingValidByteLength = pendingFile.length(),
              destFile = destFile,
          )
      if (committed) {
        val mergedDuration = base.duration + recordingResult.duration
        val mergedWaveform =
            base.waveform.appendSegment(
                base.duration,
                newRawAmplitudes = recordingResult.amplitudes,
                newDuration = recordingResult.duration,
            )
        MergeAudioRecordingResult.Success(duration = mergedDuration, waveform = mergedWaveform)
      } else {
        MergeAudioRecordingResult.Failure(MergeFailureReason.FileIo)
      }
    }
  }

  /**
   * Safe for main-tread.
   *
   * No exception.
   */
  override suspend fun quarantineOrDelete(
      path: StoragePathModel.Internal,
      quarantinePath: StoragePathModel.Shared,
  ): Boolean =
      withContext(Dispatchers.IO + NonCancellable) {
        try {
          val file = resolvePathOrThrow(path)
          if (!file.exists() || !file.isFile) return@withContext false
          if (file.length() < MIN_QUARANTINE_BYTES) {
            file.delete()
            return@withContext false
          }
          val copyResult = storageRepository.copyFile(path, quarantinePath)
          storageRepository.deleteFile(path)
          copyResult is FileCopyResult.Success
        } catch (e: Exception) {
          Timber.w(e)
          false
        }
      }

  /**
   * Main-thread safe.
   *
   * NO exception.
   */
  private suspend fun mergeAndCommit(
      baseFile: File,
      pendingFile: File,
      pendingValidByteLength: Long,
      destFile: File,
  ): Boolean =
      withContext(Dispatchers.IO + NonCancellable) {
        val tempFile = createMergingTempFile(destFile)
        val mergeResult =
            AdtsDraftScanner.mergeAdtsFiles(
                baseFile = baseFile,
                pendingFile = pendingFile,
                pendingValidByteLength = pendingValidByteLength,
                destTempFile = tempFile,
            )

        if (mergeResult.isFailure) {
          Timber.w(mergeResult.exceptionOrNull(), "mergeAdtsFiles failed for ${destFile.name}")
          runCatching { Files.deleteIfExists(tempFile.toPath()) }
          return@withContext false
        }

        commitTempFile(tempFile, destFile)
      }

  /**
   * NOT Main-thread safe, need to be put in `IO` context.
   *
   * NO exception.
   */
  private fun commitTempFile(tempFile: File, destFile: File): Boolean {
    return try {
      destFile.parentFile?.mkdirs()
      try {
        Files.move(
            tempFile.toPath(),
            destFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            tempFile.toPath(),
            destFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
      }
      true
    } catch (e: Exception) {
      Timber.w(e, "Move merged file ${tempFile.name} -> ${destFile.name} failed")
      false
    } finally {
      runCatching { Files.deleteIfExists(tempFile.toPath()) }
    }
  }

  private fun createMergingTempFile(destFile: File): File {
    val parent = destFile.parentFile ?: destFile.absoluteFile.parentFile
    return File(parent, "${destFile.name}.${Uuid.random()}.merging.tmp")
  }

  /** @throws top.fseasy.imlog.domain.PathResolveException */
  private fun resolvePathOrThrow(path: StoragePathModel.Internal) =
      storageRepository.resolveInternalPathOrThrow(path).value.toFile()

  /** Main-thread safe. */
  private suspend fun extractWaveform(path: StoragePathModel.Internal, duration: Duration) =
      waveformExtractor.extractOrNull(path, duration)?.waveform
          ?: Waveform.getSilencePlaceholder(duration)

  private companion object {
    // 低于此大小的损坏文件连 1~2 帧音频都凑不齐，直接清理，不污染用户 Shared 目录
    const val MIN_QUARANTINE_BYTES = 256L
  }
}
