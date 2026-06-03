package top.fseasy.imlog.data.paths

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 消息附件的文件路径生成规则。
 * 只负责"生成什么样的相对路径"，不负责创建文件。
 * 设计逻辑：数据库里只存 fileName, 全路径即时计算得出
 */
object MessageFilePath {

    /**
     * 生成新文件的文件名。
     * 格式: yyyy-MM-dd-HHmmss.suffix
     */
    fun generateFileName(timestampMs: Long, suffix: String): String {
        val instant = Instant.ofEpochMilli(timestampMs)
        val utc = instant.atOffset(ZoneOffset.UTC)
        val time = utc.format(DateTimeFormatter.ofPattern("dd-HHmmss-SSS-utc"))
        return "$time.$suffix"
    }

    /**
     * 根据 文件名 + userId + timestampMs，并基于 AppPath 的 root 目录，计算最终的全路径。
     * 用于显示/加载文件时即时计算。
     */
    fun absolutePath(userId: String, timestampMs: Long, fileName: String): String {
        val relativePath = relativePath(userId, timestampMs, fileName)
        val rootDir = AppPaths.messageRootDir
        return "${rootDir}/$relativePath"
    }

    /**
     * 根据文件名 + userId + timestampMs 拼接完整相对路径。
     * 用于显示/加载文件时即时计算。
     */
    private fun relativePath(userId: String, timestampMs: Long, fileName: String): String {
        val prefix = buildDirPrefix(timestampMs)
        return "user_data/$userId/$prefix/$fileName"
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