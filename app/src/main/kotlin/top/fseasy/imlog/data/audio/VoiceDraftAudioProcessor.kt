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
import top.fseasy.imlog.domain.audio.PendingAudioRecoveryResult
import top.fseasy.imlog.domain.audio.VoiceDraftAudioProcessor
import top.fseasy.imlog.domain.audio.Waveform
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.repository.StorageRepository

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
    val tempFile = File(destFile.parentFile, "${destFile.name}.merging.tmp")

    val mergeResult =
        AdtsDraftScanner.mergeAdtsFiles(
            baseFile = baseFile,
            pendingFile = pendingFile,
            pendingValidByteLength = pendingScan.validByteLength,
            destTempFile = tempFile,
        )

    if (mergeResult.isFailure) {
      tempFile.delete()
      return AppendMergeResult.PendingUnrecoverableKeepBase(
          mergeResult.exceptionOrNull()?.message ?: "Merge failed",
      )
    }

    return withContext(Dispatchers.IO) {
      if (!tempFile.renameTo(destFile)) {
        try {
          tempFile.copyTo(destFile, overwrite = true)
        } catch (e: Exception) {
          Timber.w(e, "Copy merged file to $destFile failed")
          return@withContext AppendMergeResult.PendingUnrecoverableKeepBase(
              "Move Merge failed",
          )
        } finally {
          tempFile.delete()
        }
      }
      val newDuration = baseDuration + pendingScan.duration
      val newWaveform = extractWaveform(destPath, newDuration)
      AppendMergeResult.Merged(
          duration = newDuration,
          waveform = newWaveform,
      )
    }
  }

  override suspend fun quarantineOrDelete(
      path: StoragePathModel.Internal,
      quarantinePath: StoragePathModel.Shared,
  ): Boolean =
      withContext(Dispatchers.IO + NonCancellable) {
        val file = resolvePath(path)
        if (!file.exists() || !file.isFile) return@withContext false
        if (file.length() == 0L) {
          file.delete()
          return@withContext false
        }
        val copyResult = storageRepository.copyFile(path, quarantinePath)
        storageRepository.deleteFile(path)
        copyResult is FileCopyResult.Success
      }

  private fun resolvePath(path: StoragePathModel.Internal) =
      storageRepository.resolveInternalStoragePathToAbsolutePathWithoutCreating(path).value.toFile()

  private suspend fun extractWaveform(path: StoragePathModel.Internal, duration: Duration) =
      waveformExtractor.extractOrNull(path, duration)?.waveform
          ?: Waveform.getSilencePlaceholder(duration)
}
