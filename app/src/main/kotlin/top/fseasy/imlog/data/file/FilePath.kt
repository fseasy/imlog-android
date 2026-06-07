package top.fseasy.imlog.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.data.datastore.AppPreferencesRepository
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.util.splitNameAndExtension
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val KEEP_ORIGINAL_FILENAME_MAX_CHARS = 60
private const val THUMBNAIL_DIR_NAME = "thumbnail"

@Singleton
class FileRootDir @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
) {
    val thumbnailRootDir: File = File(context.filesDir, THUMBNAIL_DIR_NAME).apply { mkdirs() }
    val messageFileRootUri: Flow<Uri?> = appPreferencesRepository.sharedStorageRootUri


}

/**
 * 消息附件的文件路径生成规则。
 * 只负责"生成什么样的相对路径"，不负责创建文件。
 * 设计逻辑：数据库里只存 filename, 全路径即时计算得出
 */
object MessageFilePathRule {

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
     * 根据文件名 + userId + timestampMs 拼接完整相对路径。
     * 用于显示/加载文件时即时计算。
     */
    fun generateFullRelativePath(
        userId: UserId,
        topicId: TopicId,
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
     * 格式: ${dd-HHmmss-SSS}-utc.$suffix
     */
    private fun generateTimePrefix(timestampMs: Long): String {
        val instant = Instant.ofEpochMilli(timestampMs)
        val utc = instant.atOffset(ZoneOffset.UTC)
        val time = utc.format(DateTimeFormatter.ofPattern("dd-HHmmss-SSSutc"))
        return "$time"
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
