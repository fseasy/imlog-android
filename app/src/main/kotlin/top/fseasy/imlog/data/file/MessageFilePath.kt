package top.fseasy.imlog.data.file

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 消息附件的文件路径生成规则。
 * 只负责"生成什么样的相对路径"，不负责创建文件。
 * 设计逻辑：数据库里只存 filename, 全路径即时计算得出
 */
object MessageFilePath {

    /**
     * 生成新文件的文件名。
     * 格式: yyyy-MM-dd-HHmmss.suffix
     */
    fun generateFilename(timestampMs: Long, suffix: String): String {
        val instant = Instant.ofEpochMilli(timestampMs)
        val utc = instant.atOffset(ZoneOffset.UTC)
        val time = utc.format(DateTimeFormatter.ofPattern("dd-HHmmss-SSS-utc"))
        return "$time.$suffix"
    }

    /**
     * 根据 文件名 + userId + timestampMs，并基于 AppPath 的 root 目录，计算最终的全路径。
     * 用于显示/加载文件时即时计算。
     * @param userId: should be the login user id!
     */
    fun absolutePath(userId: String, timestampMs: Long, filename: String): String {
        val relativePath = relativePath(userId, timestampMs, filename)
        val rootDir = AppPaths.messageRootDir
        return "${rootDir}/$relativePath"
    }

    /**
     * 根据文件名 + userId + timestampMs 拼接完整相对路径。
     * 用于显示/加载文件时即时计算。
     */
    private fun relativePath(userId: String, timestampMs: Long, filename: String): String {
        val prefix = buildDirPrefix(timestampMs)
        return "user_data/$userId/$prefix/$filename"
    }

    /**
     * @param timestampMs: something like System.currentTimeMillis()
     */
    private fun buildDirPrefix(timestampMs: Long): String {
        val instant = Instant.ofEpochMilli(timestampMs)
        val utc = instant.atOffset(ZoneOffset.UTC)
        val yearMonth = utc.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val day = utc.dayOfMonth
        val dayRange = when (day) {
            in 1..10 -> "day01-10-utc"
            in 11..20 -> "day11-20-utc"
            else -> "day21-31-utc"
        }
        return "$yearMonth/$dayRange"
    }
}

// Extensions for string (filename)

fun String?.toMessageAbsolutePath(userId: String, timestampMs: Long): String? {
    return this?.let { MessageFilePath.absolutePath(userId, timestampMs, it) }
}