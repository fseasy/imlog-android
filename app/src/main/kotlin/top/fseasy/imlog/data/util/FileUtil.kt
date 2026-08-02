package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import top.fseasy.imlog.data.constants.FILE_PROVIDER_AUTHORITIES
import top.fseasy.imlog.domain.model.FileDeleteResult
import java.io.File

fun File.toFileProviderUri(context: Context): Uri =
    FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITIES, this)

/**
 * Append multiple sub path segments to base File. No io, pure CPU operations.
 */
fun File.resolve(
    paths: List<String>,
): File = paths.fold(this) { acc, path -> File(acc, path) }

/**
 * Delete file with return FileDeleteResult.
 *
 * Sync version. not run in IO thread.
 *
 */
fun syncDeleteFile(file: File): FileDeleteResult = try {
    if (file.exists()) {
        if (file.delete()) {
            FileDeleteResult.Success
        } else {
            FileDeleteResult.Error(IllegalStateException("File delete return false without exception"))
        }
    } else {
        FileDeleteResult.FileNotExist
    }
} catch (e: Exception) {
    Timber.d(e, "Delete file $file failed")
    FileDeleteResult.Error(e)
}
