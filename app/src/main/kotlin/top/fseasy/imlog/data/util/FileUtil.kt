package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.constants.FILE_PROVIDER_AUTHORITIES
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileCopyResult
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * No exception will be thrown. Run in IO thread.
 */
suspend fun copyUriToFile(
    context: Context,
    srcUri: Uri,
    destination: File,
): FileCopyResult = withContext(Dispatchers.IO) {
    // 1. create tgt dir
    try {
        destination.parentFile?.mkdirs()
    } catch (e: SecurityException) {
        return@withContext FileCopyResult.Error.TgtPermissionDenied(e)
    } catch (e: Exception) {
        return@withContext FileCopyResult.Error.TgtOpenUnexpected(e)
    }

    // 2.open input
    val inputStream = try {
        context.contentResolver.openInputStream(srcUri)
            ?: return@withContext FileCopyResult.Error.SrcNotFound(
                FileNotFoundException("ContentResolver returned null for URI: $srcUri")
            )
    } catch (e: SecurityException) {
        return@withContext FileCopyResult.Error.SrcPermissionDenied(e)
    } catch (e: FileNotFoundException) {
        return@withContext FileCopyResult.Error.SrcNotFound(e)
    } catch (e: Exception) {
        return@withContext FileCopyResult.Error.SrcOpenUnexpected(e)
    }

    // 3. `use` to ensure resource release properly
    inputStream.use { input ->
        val outputStream = try {
            destination.outputStream()
        } catch (e: SecurityException) {
            return@withContext FileCopyResult.Error.TgtPermissionDenied(e)
        } catch (e: FileNotFoundException) {
            return@withContext FileCopyResult.Error.TgtNotFound(e)
        } catch (e: Exception) {
            return@withContext FileCopyResult.Error.TgtOpenUnexpected(e)
        }

        outputStream.use { output ->
            try {
                val bytesCopied = input.copyTo(output)
                return@withContext FileCopyResult.Success(
                    bytesCopied, AbsolutePathModel.FileModel(destination)
                )
            } catch (e: IOException) {
                return@withContext FileCopyResult.Error.CopyIOError(e)
            } catch (e: Throwable) {
                return@withContext FileCopyResult.Error.CopyUnexpected(e)
            }
        }
    }
}

fun File.toFileProviderUri(context: Context): Uri =
    FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITIES, this)
