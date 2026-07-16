package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.mapper.toUriOrNull
import top.fseasy.imlog.data.mapper.toUriOrThrow
import top.fseasy.imlog.domain.model.AbsolutePathModel
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object MimeTypeUtils {

    private const val DEFAULT_MIME_TYPE = "application/octet-stream"

    // ==========================================
    // File APIs
    // ==========================================

    fun getErrorDefaultMimeType() = DEFAULT_MIME_TYPE

    /**
     * Retrieves the MIME type of File, falling back to a default value if unresolved.
     * No exception will be thrown. Run in IO thread.
     */
    suspend fun getMimeType(file: File): String {
        return getMimeTypeOrNull(file) ?: DEFAULT_MIME_TYPE
    }

    /**
     * Retrieves the MIME type of File, or null if it cannot be determined.
     * No exception will be thrown. Run in IO thread.
     */
    suspend fun getMimeTypeOrNull(file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) return@withContext null

        // 1. Resolve via file extension
        getMimeTypeFromExtension(file.extension)?.let { return@withContext it }
        // 2. Resolve via file header bytes
        getMimeTypeFromStream { FileInputStream(file) }?.let { return@withContext it }
        // 3. have to return null
        null
    }

    // ==========================================
    // Uri APIs
    // ==========================================

    /**
     * Retrieves the MIME Type of Uri, falling back to a default value if unresolved.
     * No exception will be thrown. Run in IO thread.
     */
    suspend fun getMimeType(context: Context, uri: Uri): String {
        return getMimeTypeOrNull(context, uri) ?: DEFAULT_MIME_TYPE
    }

    /**
     * Retrieves the MIME type of Uri, or null if it cannot be determined.
     * No exception will be thrown. Run in IO thread.
     */
    suspend fun getMimeTypeOrNull(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            // 1. Resolve via ContentResolver
            var mimeType = try {
                context.contentResolver.getType(uri)
            } catch (e: Exception) {
                null
            }

            // If the type is unresolved or generic, proceed with deeper analysis
            if (mimeType == null || mimeType == DEFAULT_MIME_TYPE) {
                val extension = getExtensionFromUri(uri)

                // 2. Resolve via extension
                val extMime = extension?.let { getMimeTypeFromExtension(it) }
                if (extMime != null) {
                    mimeType = extMime
                } else {
                    // 3. Resolve via stream header bytes
                    val headerMime =
                        getMimeTypeFromStream { context.contentResolver.openInputStream(uri) }
                    if (headerMime != null) {
                        mimeType = headerMime
                    }
                }
            }

            // Return null instead of the generic type to allow clean fallback evaluation
            if (mimeType == DEFAULT_MIME_TYPE) null else mimeType
        }

    // ==========================================
    // AbsolutePathModel APIs
    // ==========================================

    /**
     * Retrieves the MIME type of absolutePathModel, falling back to a default value if unresolved.
     * No exception will be thrown. Run in IO thread.
     */
    suspend fun getMimeType(absolutePathModel: AbsolutePathModel, context: Context): String {
        return getMimeTypeOrNull(absolutePathModel, context) ?: DEFAULT_MIME_TYPE
    }

    suspend fun getMimeTypeOrNull(absolutePathModel: AbsolutePathModel, context: Context): String? {
        when (absolutePathModel) {
            is AbsolutePathModel.FileModel -> {
                return getMimeType(absolutePathModel.value)
            }

            is AbsolutePathModel.UriStrModel -> {
                val uri = absolutePathModel.value.toUriOrNull() ?: return null
                return getMimeTypeOrNull(context = context, uri = uri)
            }
        }
    }

    // ==========================================
    // Shared Internal Helpers
    // ==========================================

    /**
     * Performs MIME resolution using standard MimeTypeMap and fallback definitions.
     */
    private fun getMimeTypeFromExtension(extension: String): String? {
        if (extension.isEmpty()) return null
        val lowerExt = extension.lowercase()
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(lowerExt) ?: getFallbackMimeType(lowerExt)
    }

    /**
     * Reads the first 12 bytes from the opened stream to extract magic bytes.
     */
    private fun getMimeTypeFromStream(openStream: () -> InputStream?): String? {
        return try {
            openStream()?.use { input ->
                val header = ByteArray(12)
                val bytesRead = input.read(header)
                if (bytesRead >= 4) {
                    getMimeTypeFromHeaderBytes(header, bytesRead)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Inspects the byte array for matching file format signatures.
     */
    private fun getMimeTypeFromHeaderBytes(header: ByteArray, bytesRead: Int): String? {
        return when {
            // RIFF Formats (WAV, WEBP)
            header.startsWith(0, b("RIFF")) && bytesRead >= 12 -> {
                when {
                    header.startsWith(8, b("WAVE")) -> "audio/wav"
                    header.startsWith(8, b("WEBP")) -> "image/webp"
                    else -> null
                }
            }

            header.startsWith(0, b("BM")) -> "image/bmp"
            header.startsWith(0, b("GIF8")) -> "image/gif"
            header.startsWith(
                0, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
            ) -> "image/jpeg"

            header.startsWith(
                0, byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
            ) -> "image/png"

            header.startsWith(0, b("%PDF")) -> "application/pdf"
            header.startsWith(0, b("PK\u0003\u0004")) -> "application/zip"
            header.startsWith(0, b("ID3")) -> "audio/mpeg"
            header.startsWith(0, b("fLaC")) -> "audio/flac"
            // MP4: check "ftyp" signature at offset 4
            bytesRead >= 8 && header.startsWith(4, b("ftyp")) -> "video/mp4"
            header.startsWith(
                0, byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
            ) -> "video/webm"

            else -> null
        }
    }

    /**
     * Extracts extension from a Uri by combining standard parsing and path fallback.
     */
    private fun getExtensionFromUri(uri: Uri): String? {
        val url = uri.toString()
        var extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension.isNullOrEmpty()) {
            val path = uri.path
            if (path != null) {
                val lastDot = path.lastIndexOf('.')
                if (lastDot != -1) {
                    extension = path.substring(lastDot + 1)
                }
            }
        }
        return extension?.lowercase()
    }

    /**
     * Static manual fallback mappings for newer or omitted extensions.
     */
    private fun getFallbackMimeType(extension: String): String? {
        return when (extension) {
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "m4a" -> "audio/mp4a-latm"
            "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> null
        }
    }

    /**
     * Converts a string to an ASCII byte array for magic byte comparisons.
     */
    private fun b(str: String): ByteArray = str.toByteArray(StandardCharsets.US_ASCII)

    /**
     * Non-allocating byte comparison helper.
     */
    private fun ByteArray.startsWith(offset: Int, prefix: ByteArray): Boolean {
        if (this.size - offset < prefix.size) return false
        for (i in prefix.indices) {
            if (this[offset + i] != prefix[i]) return false
        }
        return true
    }
}