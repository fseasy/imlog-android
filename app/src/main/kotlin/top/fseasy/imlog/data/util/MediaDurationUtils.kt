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
import timber.log.Timber
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

object MediaDurationUtils {

    private const val DEFAULT_DURATION = 0

    // ==========================================
    // File APIs
    // ==========================================

    /**
     * Retrieves the duration of a media file (Audio or Video) in milliseconds.
     * Returns 0 if unresolved or if the file is invalid.
     *
     * Run in IO thread.
     */
    suspend fun getDuration(file: File): Duration {
        return getDurationOrNull(file) ?: DEFAULT_DURATION.milliseconds
    }

    /**
     * Retrieves the duration of a media file (Audio or Video) in milliseconds, or null if unresolved.
     */
    suspend fun getDurationOrNull(file: File): Duration? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) return@withContext null

        // 1. Primary: MediaMetadataRetriever
        syncGetDurationFromRetriever { retriever ->
            retriever.setDataSource(file.absolutePath)
        } ?:
        // 2. Secondary Fallback: MediaExtractor
        syncGetDurationFromExtractor { extractor ->
            extractor.setDataSource(file.absolutePath)
        }
    }

    /***
     * Alias of getDurationOrNull
     */
    suspend fun getDurationWithReadingFileOrNull(file: File): Duration? = getDurationOrNull(file)

    // ==========================================
    // Uri APIs
    // ==========================================

    /**
     * Retrieves the duration of a media Uri (Audio or Video) in milliseconds.
     * Returns 0 if unresolved or if the Uri is invalid.
     */
    suspend fun getDuration(context: Context, uri: Uri): Duration {
        return getDurationOrNull(context, uri) ?: DEFAULT_DURATION.milliseconds
    }

    /**
     * Retrieves the duration of a media Uri (Audio or Video), or null if unresolved.
     * Will first try to query system db info without reading file.
     *
     * No business exception will be thrown (but may throw CancellationException due to coroutine)
     */
    suspend fun getDurationOrNull(context: Context, uri: Uri): Duration? {
        // Fast Path - Query the ContentResolver database (Zero file parsing, instant)
        return withContext(Dispatchers.IO) {
            syncGetDurationFromContentResolver(context, uri)
        } ?: getDurationWithReadingFileOrNull(context, uri)
    }

    /**
     * Retrieves the duration of a media Uri (Audio or Video) by reading file (header or track).
     * No business exception will be thrown (but may throw CancellationException due to coroutine)
     */
    suspend fun getDurationWithReadingFileOrNull(context: Context, uri: Uri): Duration? =
        withContext(Dispatchers.IO) {
            // Standard Path - Use MediaMetadataRetriever
            syncGetDurationFromRetriever { retriever ->
                retriever.setDataSource(context, uri)
            } ?:
            // Deep Path - Use MediaExtractor with inspecting track
            syncGetDurationFromExtractor { extractor ->
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
    private fun syncGetDurationFromContentResolver(context: Context, uri: Uri): Duration? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val projection = arrayOf(MediaStore.MediaColumns.DURATION)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                        if (index != -1) {
                            val durationStr = cursor.getString(index)
                            durationStr?.toLongOrNull()?.milliseconds // in MS
                        } else null
                    } else null
                }
        } catch (e: Exception) {
            Timber.w(e, "failed to query media duration from content resolver")
            null // Handle query failure or security exceptions gracefully
        }
    }

    /**
     * Standard Path: Extracts duration using MediaMetadataRetriever.
     */
    private fun syncGetDurationFromRetriever(setDataSource: (MediaMetadataRetriever) -> Unit): Duration? {
        // NOTE: it could be replaced by Media3 MetadataRetriever. But not very necessarys
        val retriever = MediaMetadataRetriever()
        return try {
            setDataSource(retriever)
            val durationStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull()?.milliseconds // It's ms
        } catch (e: Exception) {
            Timber.w(e, "failed to get duration from retriever")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Suppress release exceptions
            }
        }
    }

    /**
     * Deep Path: Extracts duration by inspecting track structures via MediaExtractor.
     */
    private fun syncGetDurationFromExtractor(setDataSource: (MediaExtractor) -> Unit): Duration? {
        val extractor = MediaExtractor()
        return try {
            setDataSource(extractor)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    return durationUs.microseconds
                }
            }
            null
        } catch (e: Exception) {
            Timber.w(e, "failed to get duration from extractor")
            null
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
                // Suppress release exceptions
            }
        }
    }
}