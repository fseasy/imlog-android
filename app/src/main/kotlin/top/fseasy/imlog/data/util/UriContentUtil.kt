package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileCopyResult
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

enum class FileWriteMode(val value: String) {
    WRITE_TRUNCATE("wt"), WRITE_APPEND("wa"),
}

sealed interface WriteDataResult {
    data object Success : WriteDataResult
    sealed interface Error : WriteDataResult {
        val cause: Throwable

        data class PermissionDenied(override val cause: Throwable) : Error
        data class FileOpenFailed(override val cause: Throwable) : Error
        data class Unexpected(override val cause: Throwable) : Error
    }
}

/**
 * Run in IO thread.
 * No exception thrown.
 */
suspend fun writeData2Uri(
    context: Context,
    tgtFileUri: Uri,
    content: ByteArray,
): WriteDataResult = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(tgtFileUri, FileWriteMode.WRITE_TRUNCATE.value)
            ?.use { it.write(content) } ?: WriteDataResult.Error.Unexpected(
            IllegalStateException("open output stream get null")
        )
        WriteDataResult.Success
    } catch (e: SecurityException) {
        WriteDataResult.Error.PermissionDenied(e)
    } catch (e: FileNotFoundException) {
        WriteDataResult.Error.FileOpenFailed(e)
    } catch (e: Exception) {
        WriteDataResult.Error.Unexpected(e)
    }
}

/**
 * Copy file from src uri to tgt uri.
 * Effective for SAF uri and MediaStore Uri.
 */
suspend fun copyBetweenUri(
    context: Context,
    srcFileUri: Uri,
    tgtFileUri: Uri,
    writeMode: FileWriteMode = FileWriteMode.WRITE_TRUNCATE,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
): FileCopyResult = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver

    val inputStream = try {
        contentResolver.openInputStream(srcFileUri)
    } catch (e: SecurityException) {
        return@withContext FileCopyResult.Error.SrcPermissionDenied(e)
    } catch (e: FileNotFoundException) {
        return@withContext FileCopyResult.Error.SrcNotFound(e)
    } catch (e: Exception) {
        return@withContext FileCopyResult.Error.SrcOpenUnexpected(e)
    }
    if (inputStream == null) {
        return@withContext FileCopyResult.Error.SrcOpenUnexpected(
            IllegalStateException("openInputStream returned null")
        )
    }

    inputStream.use { ins ->
        val outputStream = try {
            contentResolver.openOutputStream(tgtFileUri, writeMode.value)
        } catch (e: SecurityException) {
            return@withContext FileCopyResult.Error.TgtPermissionDenied(e)
        } catch (e: FileNotFoundException) {
            return@withContext FileCopyResult.Error.TgtNotFound(e)
        } catch (e: Exception) {
            return@withContext FileCopyResult.Error.TgtOpenUnexpected(e)
        }
        if (outputStream == null) {
            return@withContext FileCopyResult.Error.TgtOpenUnexpected(
                IllegalStateException("openOutputStream returned null")
            )
        }
        // copy
        try {
            outputStream.use { outs ->
                val bytesCopied = ins.copyTo(outs, bufferSize)
                FileCopyResult.Success(
                    bytesCopied,
                    resultAbsolutePath = AbsolutePathModel.UriStrModel(tgtFileUri.toUriStr())
                )
            }
        } catch (e: IOException) {
            FileCopyResult.Error.CopyIOError(e)
        } catch (e: Exception) {
            FileCopyResult.Error.CopyUnexpected(e)
        }
    }
}

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