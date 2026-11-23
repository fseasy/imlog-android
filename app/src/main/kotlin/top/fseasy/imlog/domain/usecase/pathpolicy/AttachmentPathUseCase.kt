package top.fseasy.imlog.domain.usecase.pathpolicy

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId

@Singleton
class AttachmentPathUseCase
@Inject
constructor(private val storagePathUseCase: StoragePathUseCase) {
  fun generateInternalFilename(
      messageTimestamp: Instant,
      originalDisplayName: String,
  ) =
      storagePathUseCase.buildTimestampedFilename(
          timestamp = messageTimestamp,
          originalFilename = originalDisplayName,
      )

  /**
   * 内部临时存储位置，用作 copy 到外部的缓冲位置；底层对应 DraftPath, 因为这些缓存内容，其实可以看作 draft 也同时用来存储 voice composer draft
   * audio，这样可以省掉一个 copy 的代价（配合下面的 [isPathAlreadyInInternalCache]）
   */
  fun generateInternalCachePath(userId: UserId, cacheFilename: String) =
      storagePathUseCase.buildDraftPath(
          userId,
          filename = cacheFilename,
      )



  fun isPathAlreadyInInternalCache(path: StoragePathModel, userId: UserId): Boolean {
    // Trick: we make a dummy path and then compare them.
    // - if no filename, it can't be internal cache
    val filename = path.fullRelativePath.lastOrNull() ?: return false
    return path == generateInternalCachePath(userId, filename)
  }
}
