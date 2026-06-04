package top.fseasy.imlog.data.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.datastore.AppPreferencesRepository
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    appPreferencesRepository: AppPreferencesRepository,
    @ApplicationContext private val context: Context
) {

    /**
     * 接收外部的 Uri（相册、文件管理器、录音缓存），将其 Copy 到 ImLog 的专属时间线目录下。
     * * @return 返回相对于 AppRoot 的相对路径 (relativePath)，用于存入数据库 messages 表。
     */
    suspend fun saveMessageFile(
        userId: String,
        sourceUri: Uri,
        timestampMs: Long = System.currentTimeMillis()
    ): String = withContext(Dispatchers.IO) {

        // 1. 解析文件后缀名 (默认给个 unknown 防崩)
        val extension = getFileExtension(sourceUri) ?: "unknown"

        // 2. 生成文件名和路径
        val filename = MessageFilePath.generateFilename(timestampMs, extension)
        val absolutePath = MessageFilePath.absolutePath(userId, timestampMs, filename)
        val relativePath = MessageFilePath.relativePath(userId, timestampMs, filename) // 注意：需要将此方法改为 public

        val destFile = File(absolutePath)

        // 3. 确保父级目录 (年月/旬) 已经创建
        destFile.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        // 4. 执行 IO 流拷贝 (从 Uri 读，往 File 写)
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalArgumentException("无法打开源文件 Uri: $sourceUri")

        // 5. 返回相对路径供数据库持久化
        return@withContext relativePath
    }

    /**
     * 用于 UI 层 (如 ImageBubble, VideoBubble) 读取真实的物理文件进行渲染。
     */
    fun getFileForRender(absolutePath: String): File {
        return File(absolutePath)
    }

    /**
     * 用户删除某条记录时，彻底从磁盘抹除文件。
     */
    suspend fun deleteMessageFile(absolutePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(absolutePath)
        if (file.exists()) {
            return@withContext file.delete()
        }
        return@withContext false
    }

    /**
     * 辅助方法：从 Uri 安全提取文件扩展名
     */
    private fun getFileExtension(uri: Uri): String? {
        return if (uri.scheme == "content") {
            val mimeType = context.contentResolver.getType(uri)
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        } else {
            MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(uri.path ?: "")).toString())
        }
    }
}