package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.InternalLocation
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringConstantId
import top.fseasy.imlog.domain.util.splitNameAndExtension
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random

/**
 * define path rules for the top-level storage buckets:
 * - shared storage: mainly for user message data (backup, sync)
 * - app-specific storage:
 *
 *    internal storage: in Modern phone, we usually choose this.
 *      Same volume with external storage (if only built-in chip memory)
 *      More stable to access compared to the external storage
 *
 *    external storage: can be access by MTP (connecting to the PC).
 *      Main be unaccessible in legacy device or unknown condition.
 *      I don't know it well.
 *
 *    Let's choose Internal Storage instead.
 *
 * basic rule:
 * - shared storage:
 *    root-uri: dirname contains app-name
 *    user storage root: $root/$user_id
 *
 * - internal persistent+cache:
 *
 *    root-uri: platform dependent, don't care here.
 *
 *    user storage root: $root/$user_id/
 */
class StoragePathUseCase @Inject constructor(
    private val resourceProvider: ResourceProvider,
) {
    private val appStaticName = resourceProvider.getConstString(StringConstantId.AppStaticName)
    val defaultSharedStorageRootDirName = "${appStaticName}-storage"
    private val sharedStorageRootMarkerFilename = "${appStaticName}.txt"

    /**
     * To determine the root dir of shared storage root. Here we can't provide the final result,
     * We can only provide the kernel path-judge logic.
     *
     * @see InitializeUserStorageUseCase.determineSharedStorageRootUri
     **/
    fun needsSubDirForActualSharedStorageRoot(userSelectedRootDirName: String): Boolean {
        return userSelectedRootDirName.contains(appStaticName, ignoreCase = true)
    }

    fun buildSharedStorageRootMarkerFilePath(userId: UserId): StoragePathModel.SharedStorageOnly =
        StoragePathModel.SharedStorageOnly(
            listOf(getUserRootDirName(userId), sharedStorageRootMarkerFilename),
            root = SharedStorageRootSource.LookupByUser(userId)
        )

    /**
     * The root dir name for every user.
     */
    fun getUserRootDirName(userId: UserId): String = userId.value

    fun buildUserAvatarStoragePath(
        signInUserId: UserId,
        filename: String,
    ): StoragePathModel.DualWrite {
        return StoragePathModel.DualWrite(
            fullRelativePath = buildAvatarRelativePath(
                signInUserId, AvatarTargetName.USER, filename
            ),
            internalLocation = InternalLocation.Persistent,
            root = SharedStorageRootSource.LookupByUser(signInUserId)
        )
    }

    fun buildTopicAvatarStoragePath(
        signInUserId: UserId,
        filename: String,
    ): StoragePathModel.DualWrite {
        return StoragePathModel.DualWrite(
            fullRelativePath = buildAvatarRelativePath(
                signInUserId, AvatarTargetName.TOPIC, filename
            ),
            internalLocation = InternalLocation.Persistent,
            root = SharedStorageRootSource.LookupByUser(signInUserId)
        )
    }

    /** Add a time prefix on the given original filename. Used for semantic meaningful condition
     * Rule: $time_prefix + $truncated_original_name + .suffix
     */
    fun buildUserFriendlyTimestampedFilename(timestampMs: Long, originalFilename: String): String =
        addPrefixToFilename(formatToUtcDayAndTime(timestampMs), originalFilename)

    /** Add a timestamp + random-int on the given original filename.
     *  Used for cache name that don't need the semantic meaning but still keep a time info.
     *
     * Rule: $time_prefix + $truncated_original_name + .suffix
     */
    fun buildTimestampedFilename(timestampMs: Long, originalFilename: String) =
        addPrefixToFilename("$timestampMs-${Random.nextInt(1000)}", originalFilename)

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
     * rule: $user_root_name / message_cache / $filename (no more hierarchy)
     * Location: internal cache
     */
    fun buildMessageCacheFileStoragePath(
        userId: UserId,
        timestampMs: Long,
        filename: String,
    ) = buildInternalCacheStoragePath(
        userId,
        resourceName = ResourceName.MessageCache,
        timestampMs = timestampMs,
        filename = filename
    )

    /**
     * rule: $user_root_name / message / $topic_id / $date-hierarchy / $filename
     */
    fun buildMessageRawFileStoragePath(
        userId: UserId,
        topicId: TopicId,
        timestampMs: Long,
        filename: String,
    ): StoragePathModel.SharedStorageOnly = StoragePathModel.SharedStorageOnly(
        buildMessageFileFullRelativePath(
            userId = userId,
            resourceName = ResourceName.MessageFileRaw,
            topicId = topicId,
            timestampMs = timestampMs,
            filename = filename
        ), root = SharedStorageRootSource.LookupByUser(userId)
    )

    /**
     * rule: $user_root_name / thumbnail / $topic_id / $date-hierarchy / $filename
     */
    fun buildMessageThumbnailStoragePath(
        userId: UserId,
        topicId: TopicId,
        timestampMs: Long,
        filename: String,
    ): StoragePathModel.InternalOnly = StoragePathModel.InternalOnly(
        buildMessageFileFullRelativePath(
            userId = userId,
            resourceName = ResourceName.MessageThumbnail,
            topicId = topicId,
            timestampMs = timestampMs,
            filename = filename
        ), internalLocation = InternalLocation.Persistent
    )

    private fun buildInternalCacheStoragePath(
        userId: UserId,
        resourceName: ResourceName,
        timestampMs: Long,
        filename: String,
    ): StoragePathModel.InternalOnly = StoragePathModel.InternalOnly(
        buildList {
            addAll(buildResourceRootRelativePath(userId, resourceName = resourceName))
            add(filename)
        }, internalLocation = InternalLocation.Cache
    )

    /**
     * rule: $user_root_name / $source-name / $topic_id / $date-hierarchy / $filename
     */
    private fun buildMessageFileFullRelativePath(
        userId: UserId,
        resourceName: ResourceName,
        topicId: TopicId,
        timestampMs: Long,
        filename: String,
    ): List<String> {
        return buildList(6) {
            addAll(buildResourceRootRelativePath(userId, resourceName)) // cap=2
            add(topicId.value) // 1
            addAll(buildDatePartitionHierarchy(timestampMs)) // 2
            add(filename) // 1
        }
    }

    /**
     * format = ${dd-HHmmss-SSS}-utc (on UTC)
     */
    private fun formatToUtcDayAndTime(timestampMs: Long): String {
        return USER_FRIENDLY_UTC_TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMs))
    }

    /**
     * Generates time-based partition directory hierarchy paths, in [year-month, day_range] style.
     * Used for message files or any other high-volume resource types
     * Date is on UTC.
     *
     * Partition rules:
     * - First level: year-month (e.g., 2024-06)
     * - Second level: 10-day intervals (day01-10-utc, day11-20-utc, day21-31-utc)
     *
     * @param timestampMs Timestamp in milliseconds, e.g., System.currentTimeMillis()
     * @return List of partition paths, e.g., ["2024-06", "day11-20-utc"]
     *
     * @see [Partition naming convention documentation link]
     */
    private fun buildDatePartitionHierarchy(timestampMs: Long): List<String> {
        val instant = Instant.ofEpochMilli(timestampMs)
        val utc = instant.atOffset(ZoneOffset.UTC)
        val yearMonth = utc.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val day = utc.dayOfMonth
        val dayRange = when (day) {
            in 1..10 -> "day01-10-utc"
            in 11..20 -> "day11-20-utc"
            else -> "day21-31-utc"
        }
        return listOf(yearMonth, dayRange)
    }

    private fun buildAvatarRelativePath(
        userId: UserId,
        avatarTargetName: AvatarTargetName,
        filename: String,
    ): List<String> = buildResourceRootRelativePath(userId, ResourceName.AVATAR) + listOf(
        avatarTargetName.value, filename
    )

    private fun buildResourceRootRelativePath(
        userId: UserId,
        resourceName: ResourceName,
    ): List<String> = listOf(getUserRootDirName(userId), resourceName.value)

    companion object {
        private val USER_FRIENDLY_UTC_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS-'utc'")
                .withZone(ZoneOffset.UTC)
    }
}

private enum class ResourceName(val value: String) {
    AVATAR("avatar"), MessageFileRaw("message"), MessageThumbnail("thumbnail"), MessageCache("message_cache")
}

private enum class AvatarTargetName(val value: String) {
    USER(value = "user"), TOPIC(value = "topic")
}

private const val KEEP_ORIGINAL_FILENAME_MAX_CHARS = 60
