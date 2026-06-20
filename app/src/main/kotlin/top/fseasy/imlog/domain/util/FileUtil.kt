package top.fseasy.imlog.domain.util

import java.io.File

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
): File = resolveSubPaths(paths.toList(), createDir, lastPathIsFile)

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

fun String?.toFile(): File? = this?.let { File(it) }