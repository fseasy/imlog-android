package top.fseasy.imlog.data.audio

import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.data.commonmain.audio.AdtsDraftScanner
import top.fseasy.imlog.data.commonmain.audio.AdtsPendingScanResult
import top.fseasy.imlog.data.commonmain.audio.AdtsQuickCheckResult
import top.fseasy.imlog.data.mapper.toFile
import top.fseasy.imlog.domain.audio.AppendMergeResult
import top.fseasy.imlog.domain.audio.AudioWaveformExtractor
import top.fseasy.imlog.domain.audio.CommittedAudioCheckResult
import top.fseasy.imlog.domain.audio.FastMergeResult
import top.fseasy.imlog.domain.audio.PendingAudioRecoveryResult
import top.fseasy.imlog.domain.audio.VoiceDraftAudioProcessor
import top.fseasy.imlog.domain.audio.Waveform
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.repository.StorageRepository
import kotlin.uuid.Uuid

class AdtsVoiceDraftAudioProcessor
@Inject
constructor(
    private val storageRepository: StorageRepository,
    private val waveformExtractor: AudioWaveformExtractor,
) : VoiceDraftAudioProcessor {

  override suspend fun validateCommitted(
      path: StoragePathModel.Internal,
  ): CommittedAudioCheckResult {
    val file = resolvePath(path)
    return when (val result = AdtsDraftScanner.quickValidate(file)) {
      is AdtsQuickCheckResult.Valid -> CommittedAudioCheckResult.Valid
      AdtsQuickCheckResult.Missing -> CommittedAudioCheckResult.Missing
      is AdtsQuickCheckResult.Invalid -> CommittedAudioCheckResult.Corrupted(result.reason)
    }
  }

  override suspend fun recoverPending(
      path: StoragePathModel.Internal,
  ): PendingAudioRecoveryResult {
    val file = resolvePath(path)
    return when (val scan = AdtsDraftScanner.scanAndRepairPending(file, trimCorruptedTail = true)) {
      is AdtsPendingScanResult.Valid -> {
        val waveform = extractWaveform(path, scan.duration)
        PendingAudioRecoveryResult.Ready(duration = scan.duration, waveform = waveform)
      }
      AdtsPendingScanResult.Missing -> PendingAudioRecoveryResult.Missing
      is AdtsPendingScanResult.Corrupted -> PendingAudioRecoveryResult.Corrupted(scan.reason)
    }
  }

  override suspend fun mergeAppending(
      basePath: StoragePathModel.Internal,
      baseWaveform: Waveform?,
      baseDuration: Duration,
      pendingPath: StoragePathModel.Internal,
      destPath: StoragePathModel.Internal,
  ): AppendMergeResult {
    val baseFile = resolvePath(basePath)
    val baseCheck = AdtsDraftScanner.quickValidate(baseFile)
    if (baseCheck !is AdtsQuickCheckResult.Valid) {
      val reason = (baseCheck as? AdtsQuickCheckResult.Invalid)?.reason ?: "Base missing"
      return AppendMergeResult.BaseCorrupted(reason)
    }

    val pendingFile = resolvePath(pendingPath)
    val pendingScan = AdtsDraftScanner.scanAndRepairPending(pendingFile, trimCorruptedTail = true)
    if (pendingScan !is AdtsPendingScanResult.Valid) {
      val reason = (pendingScan as? AdtsPendingScanResult.Corrupted)?.reason ?: "Pending missing"
      return AppendMergeResult.PendingUnrecoverableKeepBase(reason)
    }

    if (baseCheck.config != pendingScan.config) {
      return AppendMergeResult.IncompatibleFormat
    }

    val destFile = resolvePath(destPath)
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

  override suspend fun fastMergeAppendingAudio(
    basePath: StoragePathModel.Internal,
    appendingPath: StoragePathModel.Internal,
    destPath: StoragePathModel.Internal,
  ): FastMergeResult =
    withContext(Dispatchers.IO + NonCancellable) {
      val baseFile = resolvePath(basePath)
      val pendingFile = resolvePath(appendingPath)
      val destFile = resolvePath(destPath)

      // 1) 快速处理空 appending（正常流程中短按秒停的常见情况）
      if (!pendingFile.exists() || pendingFile.length() == 0L) {
        return@withContext handleEmptyAppending(baseFile, destFile)
      }

      // 2) O(1) 轻量检查（共读 ~28 bytes）：确保文件合法且采样率/声道一致
      val baseCheck = AdtsDraftScanner.quickValidate(baseFile)
      if (baseCheck !is AdtsQuickCheckResult.Valid) {
        val reason = (baseCheck as? AdtsQuickCheckResult.Invalid)?.reason ?: "Base missing"
        return@withContext FastMergeResult.Failed(reason)
      }
      val pendingCheck = AdtsDraftScanner.quickValidate(pendingFile)
      if (pendingCheck !is AdtsQuickCheckResult.Valid) {
        val reason = (pendingCheck as? AdtsQuickCheckResult.Invalid)?.reason ?: "Pending invalid"
        return@withContext FastMergeResult.Failed(reason)
      }
      if (baseCheck.config != pendingCheck.config) {
        return@withContext FastMergeResult.IncompatibleFormat
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
        FastMergeResult.Merged
      } else {
        FastMergeResult.Failed("Merge or commit file failed")
      }
    }

  private suspend fun handleEmptyAppending(baseFile: File, destFile: File): FastMergeResult {
    if (!baseFile.exists() || baseFile.length() == 0L) {
      return FastMergeResult.Failed("Base missing or empty")
    }
    if (baseFile.canonicalFile != destFile.canonicalFile) {
      val tempFile = createMergingTempFile(destFile)
      try {
        tempFile.parentFile?.mkdirs()
        baseFile.copyTo(tempFile, overwrite = true)
        if (!commitTempFile(tempFile, destFile)) {
          return FastMergeResult.Failed("Copy base to dest failed")
        }
      } catch (e: Exception) {
        Timber.w(e, "Copy base to $destFile failed")
        tempFile.delete()
        return FastMergeResult.Failed(e.message ?: "Copy base failed")
      }
    }
    return FastMergeResult.AppendingEmptyKeepBase
  }

  override suspend fun quarantineOrDelete(
      path: StoragePathModel.Internal,
      quarantinePath: StoragePathModel.Shared,
  ): Boolean =
      withContext(Dispatchers.IO + NonCancellable) {
        val file = resolvePath(path)
        if (!file.exists() || !file.isFile) return@withContext false
        if (file.length() < MIN_QUARANTINE_BYTES) {
          file.delete()
          return@withContext false
        }
        val copyResult = storageRepository.copyFile(path, quarantinePath)
        storageRepository.deleteFile(path)
        copyResult is FileCopyResult.Success
      }

  private fun createMergingTempFile(destFile: File): File {
    val parent = destFile.parentFile ?: destFile.absoluteFile.parentFile
    return File(parent, "${destFile.name}.${Uuid.random()}.merging.tmp")
  }

  private suspend fun mergeAndCommit(
    baseFile: File,
    pendingFile: File,
    pendingValidByteLength: Long,
    destFile: File,
  ): Boolean =
    withContext(Dispatchers.IO + NonCancellable) {
      val parent = destFile.parentFile ?: destFile.absoluteFile.parentFile
      val tempFile = File(parent, "${destFile.name}.${UUID.randomUUID()}.merging.tmp")

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

  private fun resolvePath(path: StoragePathModel.Internal) =
      storageRepository.resolveInternalStoragePathToAbsolutePathWithoutCreating(path).value.toFile()

  private suspend fun extractWaveform(path: StoragePathModel.Internal, duration: Duration) =
      waveformExtractor.extractOrNull(path, duration)?.waveform
          ?: Waveform.getSilencePlaceholder(duration)

  private companion object {
    // 低于此大小的损坏文件连 1~2 帧音频都凑不齐，直接清理，不污染用户 Shared 目录
    const val MIN_QUARANTINE_BYTES = 256L
  }
}
