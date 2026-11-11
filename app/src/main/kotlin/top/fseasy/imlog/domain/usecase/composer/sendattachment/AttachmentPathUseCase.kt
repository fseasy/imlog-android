package top.fseasy.imlog.domain.usecase.composer.sendattachment

import javax.inject.Inject
import kotlin.time.Instant
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.StoragePathUseCase

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
