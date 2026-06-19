package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.util.splitNameAndExtension
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val KEEP_ORIGINAL_FILENAME_MAX_CHARS = 60

private enum class MessageFileTgtType(val value: String) {
    MESSAGE_RAW("message"), THUMBNAIL("thumbnail");
}

class MessageFilePathUseCase @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
) {
    /** Add a time prefix on the given original filename.
     * Rule: $time_prefix + $truncated_original_name + .suffix
     */
    fun generateFilenameByPrependTime(timestampMs: Long, originalFilename: String): String {
        val (rawName, extension) = originalFilename.splitNameAndExtension()
        val rawTruncatedName = rawName.take(KEEP_ORIGINAL_FILENAME_MAX_CHARS)
        val timePrefix = generateTimePrefix(timestampMs)
        return if (extension.isNotEmpty()) {
            "${timePrefix}.{$rawTruncatedName}.${extension}"
        } else {
            "${timePrefix}.{$rawTruncatedName}"
        }
    }

    /** Put this after the platform specific root dir/uri to get the full path.
     * rule: $user_root_name / message / $topic_id / $date-hierarchy / $filename
     */
    fun generateRawDataFullRelativePath(
        userId: UserId,
        topicId: TopicId,
        timestampMs: Long,
        filename: String,
    ): List<String> = generateFullRelativePath(
        userId = userId,
        tgtType = MessageFileTgtType.MESSAGE_RAW,
        topicId = topicId,
        timestampMs = timestampMs,
        filename = filename
    )

    /**
     * rule: $user_root_name / thumbnail / $topic_id / $date-hierarchy / $filename
     */
    fun generateThumbnailFullRelativePath(
        userId: UserId,
        topicId: TopicId,
        timestampMs: Long,
        filename: String,
    ): List<String> = generateFullRelativePath(
        userId = userId,
        tgtType = MessageFileTgtType.THUMBNAIL,
        topicId = topicId,
        timestampMs = timestampMs,
        filename = filename
    )

    /**
     * rule: $user_root_name / $tgt_type / $topic_id / $date-hierarchy / $filename
     */
    private fun generateFullRelativePath(
        userId: UserId,
        tgtType: MessageFileTgtType,
        topicId: TopicId,
        timestampMs: Long,
        filename: String,
    ): List<String> {
        val dateHierarchy = buildDatePartitionHierarchy(timestampMs)
        return buildList(6) {
            add(storagePathUseCase.getUserRootDirName(userId))
            add(tgtType.value)
            add(topicId.value)
            addAll(dateHierarchy)
            add(filename)
        }
    }

    /**
     * format = ${dd-HHmmss-SSS}-utc (date is on UTC)
     */
    private fun generateTimePrefix(timestampMs: Long): String {
        return DateTimeFormatter.ofPattern("dd-HHmmss-SSS-'utc'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(timestampMs))
    }

    /**
     * Generates time-based partition directory hierarchy paths, in [year-month, day_range] style.
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
}