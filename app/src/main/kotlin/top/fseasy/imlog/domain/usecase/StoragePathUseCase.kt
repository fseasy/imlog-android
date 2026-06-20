package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.ExternalLocation
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.FilePathModel
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringConstantId
import top.fseasy.imlog.domain.util.splitNameAndExtension
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * define path rules for the top-level storage buckets:
 * - shared storage: mainly for user message data (backup, sync)
 * - app-specific storage:
 *
 *    internal storage: mainly for db. We don't manage it here (hardcode it)
 *
 *    external storage - persistent
 *
 *    external storage - cache: like message media thumbnail
 *
 * basic rule:
 * - shared storage:
 *    root-uri: dirname contains app-name
 *    user storage root: $root/$user_id
 *
 * - external persistent+cache:
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

    fun buildSharedStorageRootMarkerFilePath(userId: UserId): FilePathModel.SharedStorageOnly =
        FilePathModel.SharedStorageOnly(
            listOf(getUserRootDirName(userId), sharedStorageRootMarkerFilename),
            root = SharedStorageRootSource.LookupByUser(userId)
        )

    /**
     * The root dir name for every user.
     */
    fun getUserRootDirName(userId: UserId): String = userId.value

    fun buildUserAvatarAbsolutePath(
        signInUserId: UserId,
        filename: String,
    ): FilePathModel.DualWrite {
        return FilePathModel.DualWrite(
            fullRelativePath = buildAvatarRelativePath(
                signInUserId, AvatarTargetName.USER, filename
            ),
            externalLocation = ExternalLocation.Persistent,
            root = SharedStorageRootSource.LookupByUser(signInUserId)
        )
    }

    fun buildTopicAvatarAbsolutePath(
        signInUserId: UserId,
        filename: String,
    ): FilePathModel.DualWrite {
        return FilePathModel.DualWrite(
            fullRelativePath = buildAvatarRelativePath(
                signInUserId, AvatarTargetName.TOPIC, filename
            ),
            externalLocation = ExternalLocation.Persistent,
            root = SharedStorageRootSource.LookupByUser(signInUserId)
        )
    }

    /** Add a time prefix on the given original filename.
     * Rule: $time_prefix + $truncated_original_name + .suffix
     */
    fun prependDayAndTimeToFilename(timestampMs: Long, originalFilename: String): String {
        val (rawName, extension) = originalFilename.splitNameAndExtension()
        val rawTruncatedName = rawName.take(KEEP_ORIGINAL_FILENAME_MAX_CHARS)
        val timePrefix = formatToUtcDayAndTime(timestampMs)
        return if (extension.isNotEmpty()) {
            "${timePrefix}.{$rawTruncatedName}.${extension}"
        } else {
            "${timePrefix}.{$rawTruncatedName}"
        }
    }

    /**
     * rule: $user_root_name / message / $topic_id / $date-hierarchy / $filename
     */
    fun buildMessageRawAbsolutePath(
        userId: UserId,
        topicId: TopicId,
        timestampMs: Long,
        filename: String,
    ): FilePathModel.SharedStorageOnly = FilePathModel.SharedStorageOnly(
        buildMessageFileFullRelativePath(
            userId = userId,
            resourceName = ResourceName.MessageFileRaw,
            topicId = topicId,
            timestampMs = timestampMs,
            filename = filename
        ),
        root = SharedStorageRootSource.LookupByUser(userId)
    )

    /**
     * rule: $user_root_name / thumbnail / $topic_id / $date-hierarchy / $filename
     */
    fun buildMessageThumbnailAbsolutePath(
        userId: UserId,
        topicId: TopicId,
        timestampMs: Long,
        filename: String,
    ): FilePathModel.ExternalOnly = FilePathModel.ExternalOnly(
        buildMessageFileFullRelativePath(
            userId = userId,
            resourceName = ResourceName.MessageThumbnail,
            topicId = topicId,
            timestampMs = timestampMs,
            filename = filename
        ), externalLocation = ExternalLocation.Cache
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
        return UTC_DAY_AND_TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMs))
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
        private val UTC_DAY_AND_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-HHmmss-SSS-'utc'")
            .withZone(ZoneOffset.UTC)
    }
}

private enum class ResourceName(val value: String) {
    AVATAR("avatar"), MessageFileRaw("message"), MessageThumbnail("thumbnail")
}

private enum class AvatarTargetName(val value: String) {
    USER(value = "user"), TOPIC(value = "topic")
}

private const val KEEP_ORIGINAL_FILENAME_MAX_CHARS = 60
