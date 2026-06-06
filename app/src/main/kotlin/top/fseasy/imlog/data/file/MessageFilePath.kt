package top.fseasy.imlog.data.file

import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.util.splitNameAndExtension
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val KEEP_ORIGINAL_FILENAME_MAX_CHARS = 60;

/**
 * 消息附件的文件路径生成规则。
 * 只负责"生成什么样的相对路径"，不负责创建文件。
 * 设计逻辑：数据库里只存 filename, 全路径即时计算得出
 */
object MessageFilePath {

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

    /**
     * 格式: ${dd-HHmmss-SSS}-utc.$suffix
     */
    private fun generateTimePrefix(timestampMs: Long): String {
        val instant = Instant.ofEpochMilli(timestampMs)
        val utc = instant.atOffset(ZoneOffset.UTC)
        val time = utc.format(DateTimeFormatter.ofPattern("dd-HHmmss-SSSutc"))
        return "$time"
    }

    /**
     * 根据文件名 + userId + timestampMs 拼接完整相对路径。
     * 用于显示/加载文件时即时计算。
     */
    fun fullRelativePath(
        userId: UserId,
        topicId: UserId,
        timestampMs: Long,
        filename: String,
    ): List<String> {
        val subPrefixDirs = buildDirPrefix(timestampMs)
        return buildList(6) {
            add("user_data")
            add(userId.value)
            add(topicId.value)
            addAll(subPrefixDirs)
            add(filename)
        }
    }

    /**
     * @param timestampMs: something like System.currentTimeMillis()
     */
    private fun buildDirPrefix(timestampMs: Long): List<String> {
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
