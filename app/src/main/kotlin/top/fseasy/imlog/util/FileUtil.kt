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

    context.contentResolver.openInputStream(uri)
        ?.use { input ->
            destination.outputStream()
                .use { output ->
                    input.copyTo(output)
                }
        } ?: throw FileNotFoundException("Can not open file: $uri")

    return destination
}

/**
 * Extract path name by substring-after-last.
 * not stable enough for dirty paths. Use it only when you know the path is very clean.
 */
fun String?.pathNameBySubstring(): String? {
    return this?.let { substringAfterLast("/") }
}

fun String.splitNameAndExtension(): Pair<String, String> {
    val lastDotIndex = lastIndexOf(".")
    return if (lastDotIndex == -1) {
        this to ""
    } else {
        substring(0, lastDotIndex) to substring(lastDotIndex + 1)
    }
}

/**
 * Append multiple sub path segments to base File (vararg version)
 * @param createDir if true, will create dirs along with all intermediate dirs
 * @param lastPathIsFile if true, will only create dirs of the final File's parent.
 *                       else create dirs for final File
 */
fun File.resolveSubPaths(
    vararg paths: String,
    createDir: Boolean = false,
    lastPathIsFile: Boolean = true,
): File = paths.fold(this) { acc, path -> File(acc, path) }
    .also {
        createDirsHelper(it, createDir, lastPathIsFile)
    }

/**
 * Append multiple sub path segments to base File (List version)
 */
fun File.resolveSubPaths(
    paths: List<String>,
    createDir: Boolean = false,
    lastPathIsFile: Boolean = true,
): File = paths.fold(this) { acc, path -> File(acc, path) }
    .also {
        createDirsHelper(it, createDir, lastPathIsFile)
    }

private fun createDirsHelper(
    file: File,
    createDir: Boolean = false,
    lastPathIsFile: Boolean = true,
) {
    if (!createDir) {
        return
    }
    val dirFile = when (lastPathIsFile) {
        false -> file
        true -> file.parentFile
    }
    dirFile?.mkdirs()
}