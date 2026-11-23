package top.fseasy.imlog.domain.audio

import kotlin.time.Duration
import top.fseasy.imlog.domain.model.StoragePathModel

interface VoiceDraftAudioProcessor {

  /** 快速校验已定稿音频（Committed / Appending 的 base） */
  suspend fun validateCommitted(path: StoragePathModel.Internal): CommittedAudioCheckResult

  /** 校验、修复（如裁掉末尾残帧）并提取单个待定稿录音的时长与波形 */
  suspend fun recoverPending(path: StoragePathModel.Internal): PendingAudioRecoveryResult

  /** 校验并将 pending 合并到 base，输出到 [destPath]（建议先写临时文件再原子替换）， 返回合并后的新时长与波形。 */
  suspend fun mergeAppending(
      basePath: StoragePathModel.Internal,
      baseDuration: Duration,
      pendingPath: StoragePathModel.Internal,
      destPath: StoragePathModel.Internal,
  ): AppendMergeResult

  /**
   * 将损坏文件移入隔离区（或删除），释放原路径. 返回是否移入成功.
   *
   * Safe for main-thread. No exception
   */
  suspend fun quarantineOrDelete(
      path: StoragePathModel.Internal,
      quarantinePath: StoragePathModel.Shared,
  ): Boolean
}

sealed interface CommittedAudioCheckResult {
  data object Valid : CommittedAudioCheckResult

  data object Missing : CommittedAudioCheckResult

  data class Corrupted(val reason: String) : CommittedAudioCheckResult
}

sealed interface PendingAudioRecoveryResult {
  data class Ready(val duration: Duration, val waveform: Waveform) : PendingAudioRecoveryResult

  data object Missing : PendingAudioRecoveryResult

  data class Corrupted(val reason: String) : PendingAudioRecoveryResult
}

sealed interface AppendMergeResult {
  data class Merged(val duration: Duration, val waveform: Waveform) : AppendMergeResult

  /** pending 丢失或彻底损坏，但 base 依然健康 -> UseCase 可直接保留 base */
  data class PendingUnrecoverableKeepBase(val reason: String) : AppendMergeResult

  /** base 本身已经损坏（或丢失） */
  data class BaseCorrupted(val reason: String) : AppendMergeResult

  /** 两个片段编码参数不一致，无法直接合并 */
  data object IncompatibleFormat : AppendMergeResult
}
