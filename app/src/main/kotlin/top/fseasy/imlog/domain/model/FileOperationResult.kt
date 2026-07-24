package top.fseasy.imlog.domain.model

import java.io.FileNotFoundException
import java.io.IOException

sealed interface FileCopyResult {
    data class Success(val bytesCopied: Long, val resultAbsolutePath: AbsolutePathModel) :
        FileCopyResult

    sealed interface Error : FileCopyResult {
        val cause: Throwable

        // Source related errors
        data class SrcPermissionDenied(override val cause: SecurityException) : Error
        data class SrcNotFound(override val cause: FileNotFoundException) : Error
        data class SrcOpenUnexpected(override val cause: Throwable) : Error

        // Target related errors
        data class TgtPermissionDenied(override val cause: SecurityException) : Error
        data class TgtNotFound(override val cause: FileNotFoundException) : Error
        data class TgtOpenUnexpected(override val cause: Exception) : Error

        // Copy process errors
        data class CopyIOError(override val cause: IOException) :
            Error

        data class CopyUnexpected(override val cause: Throwable) : Error
    }
}

sealed interface FileDeleteResult {
    data object Success : FileDeleteResult
    data object FileNotExist : FileDeleteResult
    data class Error(val cause: Throwable) : FileDeleteResult
}