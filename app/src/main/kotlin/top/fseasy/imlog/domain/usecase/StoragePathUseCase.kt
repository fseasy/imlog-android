/**
 * define path rules for the top-level storage buckets:
 * - shared storage: mainly for user message data (backup, sync)
 * - app-specific storage:
 *
 *   internal storage: in Modern phone, we usually choose this. Same volume with external storage
 *   (if only built-in chip memory) More stable to access compared to the external storage
 *
 *   external storage: can be access by MTP (connecting to the PC). Main be unaccessible in legacy
 *   device or unknown condition. I don't know it well.
 *
 *   Let's choose Internal Storage instead.
 *
 * basic rule:
 * - shared storage: root-uri: dirname contains app-name user storage root: $root/$user_id
 *
 * - internal persistent+cache:
 *
 *   root-uri: platform dependent, don't care here.
 *
 *   user storage root: $root/$user_id/
 */
package top.fseasy.imlog.domain.usecase

import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import top.fseasy.imlog.domain.model.InternalLocation
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringConstantId
import top.fseasy.imlog.domain.util.splitNameAndExtension
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Instant

/**
 * Why Singleton: Will be used frequently and widely; has member val
 *
 * # File Hierarchy
 *
 * ## Internal Cache
 *
 * ```
 * Internal Cache/
 * └── $user_id/
 *     └── message_cache/
 *         └── $filename
 * ```
 *
 * ## Internal Persistent
 *
 * ```
 * Internal Persistent/
 * └── $user_id/
 *     ├── avatar/
 *     │   ├── topic/
 *     │   │   └── $avatar_filename
 *     │   └── user/
 *     │       └── $avatar_filename
 *     └── thumbnail/
 *         └── $topic_id/
 *             └── $date-hierarchy (yyyy-mm/day-range-utc, example: `2026-07/day01-10-utc`)
 *                 └── $filename
 * ```
 *
 * ## Shared-Storage
 *
 * ```
 * Shared-Storage/
 * └── $user_id/
 *     ├── message/
 *     │   └── $topic_id/
 *     │       └── $date-hierarchy/
 *     │           └── $filename
 *     └── avatar/ (dual write)
 *         ├── topic/
 *         │   └── $avatar_filename
 *         └── user/
 *             └── $avatar_filename
 * ```
 */
@Singleton
class StoragePathUseCase
@Inject
constructor(
    resourceProvider: ResourceProvider,
) {
  private val appStaticName = resourceProvider.getConstString(StringConstantId.AppStaticName)
  val defaultSharedStorageRootDirName = "${appStaticName}-storage"
  private val sharedStorageRootMarkerFilename = "${appStaticName}.txt"

  /**
   * To determine the root dir of shared storage root. Here we can't provide the final result, We
   * can only provide the kernel path-judge logic.
   *
   * @see InitializeUserStorageUseCase.determineSharedStorageRootUri
   */
  fun needsSubDirForActualSharedStorageRoot(userSelectedRootDirName: String): Boolean {
    return userSelectedRootDirName.contains(appStaticName, ignoreCase = true)
  }

  /**
   * Marker location.
   *
   * = `$SHARED-STORAGE-ROOT / $user / $markername`
   */
  fun buildSharedStorageRootMarkerFilePath(userId: UserId): StoragePathModel.SharedStorageOnly =
      StoragePathModel.SharedStorageOnly(
          listOf(getUserRootDirName(userId), sharedStorageRootMarkerFilename),
          root = SharedStorageRootSource.LookupByUser(userId),
      )

  /**
   * The root dir name for every user.
   *
   * = `$user.id`
   */
  fun getUserRootDirName(userId: UserId): String = userId.value

  /** = `$user / avatar / user / $filename` */
  fun buildUserAvatarStoragePath(
      signInUserId: UserId,
      filename: String,
  ): StoragePathModel.DualWrite {
    return StoragePathModel.DualWrite(
        fullRelativePath =
            buildAvatarRelativePath(
                signInUserId,
                AvatarTargetName.USER,
                filename,
            ),
        internalLocation = InternalLocation.Persistent,
        root = SharedStorageRootSource.LookupByUser(signInUserId),
    )
  }

  /** = `$user / avatar / topic / $filename` */
  fun buildTopicAvatarStoragePath(
      signInUserId: UserId,
      filename: String,
  ): StoragePathModel.DualWrite {
    return StoragePathModel.DualWrite(
        fullRelativePath =
            buildAvatarRelativePath(
                signInUserId,
                AvatarTargetName.TOPIC,
                filename,
            ),
        internalLocation = InternalLocation.Persistent,
        root = SharedStorageRootSource.LookupByUser(signInUserId),
    )
  }

  /**
   * Add a time prefix on the given original filename. Used for semantic meaningful condition
   *
   * Rule: `$time_prefix + $truncated_original_name + .suffix`
   */
  fun buildUserFriendlyTimestampedFilename(
      timestamp: kotlin.time.Instant,
      originalFilename: String,
  ): String = addPrefixToFilename(formatToUtcDayAndTime(timestamp), originalFilename)

  /**
   * Add a timestamp + random-int on the given original filename. Used for cache name that don't
   * need the semantic meaning but still keep a time info.
   *
   * Rule: `$time_prefix + $truncated_original_name + .suffix`
   */
  fun buildTimestampedFilename(timestamp: kotlin.time.Instant, originalFilename: String): String {
    val prefix = "${timestamp.toEpochMilliseconds()}-${Random.nextInt(1000)}"
    return addPrefixToFilename(prefix, originalFilename)
  }

  private fun addPrefixToFilename(prefix: String, originalFilename: String): String {
    val (rawName, extension) = originalFilename.splitNameAndExtension()
    val rawTruncatedName = rawName.take(KEEP_ORIGINAL_FILENAME_MAX_CHARS)
    return if (extension.isNotEmpty()) {
      "${prefix}.{$rawTruncatedName}.${extension}"
    } else {
      "${prefix}.{$rawTruncatedName}"
    }
  }

  /**
   * Build message file path for message cache file.
   *
   * rule: `$user_root_name / message_cache / $filename (no more hierarchy)`
   *
   * Location: internal cache
   */
  fun buildMessageCacheFileStoragePath(
      userId: UserId,
      filename: String,
  ) =
      buildInternalCacheStoragePath(
          userId,
          resourceName = ResourceName.MessageCache,
          filename = filename,
      )

  fun buildTemporaryCacheFileStoragePath(
      userId: UserId,
      filename: String,
  ) =
      buildInternalCacheStoragePath(
          userId,
          resourceName = ResourceName.TemporaryCache,
          filename = filename,
      )

  /** rule: `$user_root_name / message / $topic_id / $date-hierarchy / $filename` */
  fun buildMessageRawFileStoragePath(
      userId: UserId,
      topicId: TopicId,
      timestamp: kotlin.time.Instant,
      filename: String,
  ): StoragePathModel.SharedStorageOnly =
      StoragePathModel.SharedStorageOnly(
          buildMessageFileFullRelativePath(
              userId = userId,
              resourceName = ResourceName.MessageFileRaw,
              topicId = topicId,
              timestamp = timestamp,
              filename = filename,
          ),
          root = SharedStorageRootSource.LookupByUser(userId),
      )

  /** rule: `$user_root_name / thumbnail / $topic_id / $date-hierarchy / $filename` */
  fun buildMessageThumbnailStoragePath(
      userId: UserId,
      topicId: TopicId,
      timestamp: Instant,
      filename: String,
  ): StoragePathModel.InternalOnly =
      StoragePathModel.InternalOnly(
          buildMessageFileFullRelativePath(
              userId = userId,
              resourceName = ResourceName.MessageThumbnail,
              topicId = topicId,
              timestamp = timestamp,
              filename = filename,
          ),
          internalLocation = InternalLocation.Persistent,
      )

  /** = `$STORAGE-CACHE-ROOT / $user / $resourceName / $filename` */
  private fun buildInternalCacheStoragePath(
      userId: UserId,
      resourceName: ResourceName,
      filename: String,
  ): StoragePathModel.InternalOnly =
      StoragePathModel.InternalOnly(
          buildList {
            addAll(buildResourceRootRelativePath(userId, resourceName = resourceName))
            add(filename)
          },
          internalLocation = InternalLocation.Cache,
      )

  /** rule: `$user_root_name / $source-name / $topic_id / $date-hierarchy / $filename` */
  private fun buildMessageFileFullRelativePath(
      userId: UserId,
      resourceName: ResourceName,
      topicId: TopicId,
      timestamp: kotlin.time.Instant,
      filename: String,
  ): List<String> {
    return buildList(6) {
      addAll(buildResourceRootRelativePath(userId, resourceName)) // cap=2
      add(topicId.value) // 1
      addAll(buildDatePartitionHierarchy(timestamp)) // 2
      add(filename) // 1
    }
  }

  /** format = ${dd-HHmmss-SSS}-utc (on UTC) */
  private fun formatToUtcDayAndTime(timestamp: kotlin.time.Instant): String {
    return timestamp.format(USER_FRIENDLY_UTC_TIME_FORMATTER, UtcOffset.ZERO)
  }

  /**
   * Generates time-based partition directory hierarchy paths, in [year-month, day_range] style.
   * Used for message files or any other high-volume resource types Date is on UTC.
   *
   * Partition rules:
   * - First level: year-month (e.g., 2024-06)
   * - Second level: 10-day intervals (day01-10-utc, day11-20-utc, day21-31-utc)
   *
   * @param timestamp Timestamp in milliseconds, e.g., System.currentTimeMillis()
   * @return List of partition paths, e.g., ["2024-06", "day11-20-utc"]
   * @see [Partition naming convention documentation link]
   */
  private fun buildDatePartitionHierarchy(timestamp: kotlin.time.Instant): List<String> {
    val date = timestamp.toLocalDateTime(TimeZone.UTC).date

    val yearMonth = buildString {
      append(date.year)
      append('-')
      append(date.month.number.toString().padStart(2, '0'))
    }

    val dayRange =
        when (date.day) {
          in 1..10 -> "day01-10-utc"
          in 11..20 -> "day11-20-utc"
          else -> "day21-31-utc"
        }

    return listOf(yearMonth, dayRange)
  }

  /** = `$user / avatar / $avatarTarget / $filename` */
  private fun buildAvatarRelativePath(
      userId: UserId,
      avatarTargetName: AvatarTargetName,
      filename: String,
  ): List<String> =
      buildResourceRootRelativePath(userId, ResourceName.AVATAR) +
          listOf(
              avatarTargetName.value,
              filename,
          )

  /** = `$userRoot / $resourceName` */
  private fun buildResourceRootRelativePath(
      userId: UserId,
      resourceName: ResourceName,
  ): List<String> = listOf(getUserRootDirName(userId), resourceName.value)

  companion object {
    private val USER_FRIENDLY_UTC_TIME_FORMATTER = DateTimeComponents.Format {
      year()
      monthNumber()
      day()
      char('-')
      hour()
      minute()
      second()
      char('-')
      secondFraction(3) // 对应原来的 SSS
      chars("-utc")
    }
  }
}

private enum class ResourceName(val value: String) {
  AVATAR("avatar"),
  MessageFileRaw("message"),
  MessageThumbnail("thumbnail"),
  MessageCache("message_cache"),
  TemporaryCache("temp_cache"),
}

private enum class AvatarTargetName(val value: String) {
  USER(value = "user"),
  TOPIC(value = "topic"),
}

private const val KEEP_ORIGINAL_FILENAME_MAX_CHARS = 60
