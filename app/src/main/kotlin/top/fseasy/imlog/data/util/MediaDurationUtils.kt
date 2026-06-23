package top.fseasy.imlog.data.util

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaDurationUtils {

    private const val DEFAULT_DURATION = 0L

    // ==========================================
    // File APIs
    // ==========================================

    /**
     * Retrieves the duration of a media file (Audio or Video) in milliseconds.
     * Returns 0 if unresolved or if the file is invalid.
     */
    suspend fun getDuration(file: File): Long {
        return getDurationOrNull(file) ?: DEFAULT_DURATION
    }

    /**
     * Retrieves the duration of a media file (Audio or Video) in milliseconds, or null if unresolved.
     */
    suspend fun getDurationOrNull(file: File): Long? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) return@withContext null

        // 1. Primary: MediaMetadataRetriever
        getDurationFromRetriever { retriever ->
            retriever.setDataSource(file.absolutePath)
        } ?:
        // 2. Secondary Fallback: MediaExtractor
        getDurationFromExtractor { extractor ->
            extractor.setDataSource(file.absolutePath)
        }
    }

    // ==========================================
    // Uri APIs
    // ==========================================

    /**
     * Retrieves the duration of a media Uri (Audio or Video) in milliseconds.
     * Returns 0 if unresolved or if the Uri is invalid.
     */
    suspend fun getDuration(context: Context, uri: Uri): Long {
        return getDurationOrNull(context, uri) ?: DEFAULT_DURATION
    }

    /**
     * Retrieves the duration of a media Uri (Audio or Video) in milliseconds, or null if unresolved.
     */
    suspend fun getDurationOrNull(context: Context, uri: Uri): Long? = withContext(Dispatchers.IO) {
        // Step 1: Fast Path - Query the ContentResolver database (Zero file parsing, instant)
        getDurationFromContentResolver(context, uri) ?:
        // Step 2: Standard Path - Use MediaMetadataRetriever
        getDurationFromRetriever { retriever ->
            retriever.setDataSource(context, uri)
        } ?:
        // Step 3: Deep Path - Use MediaExtractor
        getDurationFromExtractor { extractor ->
            extractor.setDataSource(context, uri, null)
        }
    }

    // ==========================================
    // Shared Internal Helpers
    // ==========================================

    /**
     * Fast Path: Queries the system MediaStore provider database directly.
     * Only works for 'content://' Uris that expose a duration column.
     */
    private fun getDurationFromContentResolver(context: Context, uri: Uri): Long? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val projection = arrayOf(MediaStore.MediaColumns.DURATION)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                        if (index != -1) {
                            val durationStr = cursor.getString(index)
                            durationStr?.toLongOrNull()
                        } else null
                    } else null
                }
        } catch (e: Exception) {
            null // Handle query failure or security exceptions gracefully
        }
    }

    /**
     * Standard Path: Extracts duration using MediaMetadataRetriever.
     */
    private fun getDurationFromRetriever(setDataSource: (MediaMetadataRetriever) -> Unit): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            setDataSource(retriever)
            val durationStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Suppress release exceptions
            }
        }
    }

    /**
     * Deep Path: Extracts duration by inspecting track structures via MediaExtractor.
     */
    private fun getDurationFromExtractor(setDataSource: (MediaExtractor) -> Unit): Long? {
        val extractor = MediaExtractor()
        return try {
            setDataSource(extractor)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    if (durationUs > 0) {
                        return durationUs / 1000 // Convert microseconds to milliseconds
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // Suppress release exceptions
            }
        }
    }
}