package top.fseasy.imlog.util

// utils/FileUtil.kt
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 将 Uri 指向的内容复制到应用内部存储。
 *
 * @param context 用于访问 ContentResolver
 * @param uri 源文件 Uri
 * @param destination 目标文件，调用方自行控制路径和文件名
 * @return 成功复制的目标文件
 * @throws FileNotFoundException 无法读取源文件
 * @throws IOException 复制过程中发生 IO 错误
 */
@Throws(IOException::class)
fun copyUriToFile(context: Context, uri: Uri, destination: File): File {
    // 确保父目录存在
    destination.parentFile?.mkdirs()

    context.contentResolver.openInputStream(uri)?.use { input ->
        destination.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw FileNotFoundException("Can not open file: $uri")

    return destination
}

/**
 * 在应用内部存储中生成文件，自动创建父目录。
 *
 * @param context 用于获取 filesDir
 * @param relativePath 相对于 filesDir 的完整路径，如 "images/photo_123.jpg"
 * @return 文件对象，父目录已确保存在
 */
fun internalFile(context: Context, relativePath: String): File {
    val file = File(context.filesDir, relativePath)
    file.parentFile?.mkdirs()
    return file
}