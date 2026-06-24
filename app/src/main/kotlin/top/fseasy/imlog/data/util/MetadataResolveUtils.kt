package top.fseasy.imlog.data.util

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.EnumMap

data class AudioMetaData(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val duration: Long,
)

data class VideoMetaData(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val duration: Long,
    val width: Int,
    val height: Int,
)

data class ImageMetaData(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

/**
 * A higher wrapper for file metadata resolving.
 * It depends on MemiTypeUtils and MediaDurationUtils in fallback route.
 */
object MetadataResolveUtils {
    /**
     * RUN IN IO.
     * @param defaultName if methods failed to get filename, return this one instead
     */
    suspend fun getDisplayNameOrDefault(
        context: Context,
        uri: Uri,
        defaultName: String,
    ): String = withContext(Dispatchers.IO) {
        val fields = setOf(
            MetaDataField.DISPLAY_NAME,
        )
        val result = syncQueryMultipleMetadataOnUriOrNull(context, uri, fields)
        result.displayName ?: getDisplayNameFallbackOrDefault(uri, defaultName)
    }

    /***
     * Run IN IO.
     * No exceptions will be thrown.
     */
    suspend fun forAudioUri(
        context: Context,
        uri: Uri,
    ): AudioMetaData = withContext(Dispatchers.IO) {
        val fields = setOf(
            MetaDataField.DISPLAY_NAME,
            MetaDataField.FILE_SIZE,
            MetaDataField.MIME_TYPE,
            MetaDataField.DURATION
        )
        val result = syncQueryMultipleMetadataOnUriOrNull(context, uri, fields)

        val displayName =
            result.displayName ?: getDisplayNameFallbackOrDefault(uri, "unknown_audio")
        val fileSize = result.fileSize ?: syncGetFileSizeFallback(context, uri)
        val mimeType = result.mimeType ?: MimeTypeUtils.getMimeTypeOrNull(context, uri) ?: "audio/*"
        val duration = result.duration ?: MediaDurationUtils.getDuration(context, uri)

        AudioMetaData(
            displayName = displayName, fileSize = fileSize, mimeType = mimeType, duration = duration
        )
    }

    /***
     * Run IN IO.
     * No exceptions will be thrown.
     */
    suspend fun forVideoUri(
        context: Context,
        uri: Uri,
    ): VideoMetaData = withContext(Dispatchers.IO) {
        val fields = setOf(
            MetaDataField.DISPLAY_NAME,
            MetaDataField.FILE_SIZE,
            MetaDataField.MIME_TYPE,
            MetaDataField.DURATION,
            MetaDataField.WIDTH,
            MetaDataField.HEIGHT
        )
        val result = syncQueryMultipleMetadataOnUriOrNull(context, uri, fields)

        val displayName =
            result.displayName ?: getDisplayNameFallbackOrDefault(uri, "unknown_video.mp4")
        val fileSize = result.fileSize ?: syncGetFileSizeFallback(context, uri)
        val mimeType = result.mimeType ?: MimeTypeUtils.getMimeTypeOrNull(context, uri) ?: "video/*"
        var duration = result.duration
        var width = result.width
        var height = result.height

        // call MediaMetadataRetriever if any missing
        if (duration == null || width == null || height == null) {
            val fallback = syncGetVideoMetadataOrZero(context, uri)
            if (duration == null) duration = fallback.duration
            if (width == null) width = fallback.width
            if (height == null) height = fallback.height
        }

        VideoMetaData(
            displayName = displayName,
            fileSize = fileSize,
            mimeType = mimeType,
            duration = duration,
            width = width,
            height = height
        )
    }

    /***
     * Run IN IO.
     * No exceptions will be thrown.
     */
    suspend fun forImageUri(
        context: Context,
        uri: Uri,
    ): ImageMetaData = withContext(Dispatchers.IO) {
        val fields = setOf(
            MetaDataField.DISPLAY_NAME,
            MetaDataField.FILE_SIZE,
            MetaDataField.MIME_TYPE,
            MetaDataField.WIDTH,
            MetaDataField.HEIGHT
        )
        val result = syncQueryMultipleMetadataOnUriOrNull(context, uri, fields)

        val displayName =
            result.displayName ?: getDisplayNameFallbackOrDefault(uri, "unknown_img.jpg")
        val fileSize = result.fileSize ?: syncGetFileSizeFallback(context, uri)
        val mimeType = result.mimeType ?: MimeTypeUtils.getMimeTypeOrNull(context, uri) ?: "image/*"
        var width = result.width
        var height = result.height

        // if any missing, call fallback to set
        if (width == null || height == null) {
            val fallback = syncGetImageDimensionFallback(context, uri)
            if (width == null) width = fallback.width
            if (height == null) height = fallback.height
        }

        ImageMetaData(
            displayName = displayName,
            fileSize = fileSize,
            mimeType = mimeType,
            width = width,
            height = height
        )
    }


    /** FALLBACK: Get path's last part name (filepath -> filename, dirpath -> dirname)
     * use This only when metadata.displayName is null
     */
    private fun getDisplayNameFallbackOrDefault(uri: Uri, default: String): String {
        // Uri.lastPathSegment could be null.
        return uri.lastPathSegment?.let { Uri.decode(it) } ?: default
    }

    private fun syncGetFileSizeFallback(context: Context, uri: Uri): Long {
        // try to use FileDescriptor to get actual size
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?.use {
                    it.length
                } ?: 0L
        } catch (e: Exception) {
            Timber.i(e, "Failed to get file size of [$uri]")
            0L
        }
    }

    private class VideoRetrieverData(
        val duration: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
    )

    /**
     * batch get duration, width, height by MediaMetadataRetriever
     */
    private fun syncGetVideoMetadataOrZero(context: Context, uri: Uri): VideoRetrieverData {
        var retriever: android.media.MediaMetadataRetriever? = null
        return try {
            retriever = android.media.MediaMetadataRetriever()
                .apply {
                    setDataSource(context, uri)
                }
            val duration =
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLong() ?: 0L
            val width =
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toInt() ?: 0
            val height =
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toInt() ?: 0
            VideoRetrieverData(duration, width, height)
        } catch (e: Exception) {
            Timber.i(e, "failed to extra metadata by MediaMetadataRetriever")
            VideoRetrieverData()
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {
            }
        }
    }


    private data class ImageDimension(val width: Int, val height: Int)

    /**
     * Read the image boundary without decoding the whole image. It's high efficiency.
     * Sync BLOCKING IO.
     */
    private fun syncGetImageDimensionFallback(context: Context, uri: Uri): ImageDimension {
        return try {
            context.contentResolver.openInputStream(uri)
                ?.use { stream ->
                    val options = BitmapFactory.Options()
                        .apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    ImageDimension(options.outWidth, options.outHeight)
                } ?: ImageDimension(0, 0)
        } catch (e: Exception) {
            Timber.i(e, "Failed to get image dimension by decodeStream")
            ImageDimension(0, 0)
        }
    }


    private enum class MetaDataField {
        DISPLAY_NAME, FILE_SIZE, MIME_TYPE, DURATION, WIDTH, HEIGHT
    }

    private class MetadataResult(private val data: EnumMap<MetaDataField, Any>) {
        val displayName: String? get() = getString(MetaDataField.DISPLAY_NAME)
        val fileSize: Long? get() = getLong(MetaDataField.FILE_SIZE)
        val mimeType: String? get() = getString(MetaDataField.MIME_TYPE)
        val duration: Long? get() = getLong(MetaDataField.DURATION)
        val width: Int? get() = getInt(MetaDataField.WIDTH)
        val height: Int? get() = getInt(MetaDataField.HEIGHT)

        private fun getString(field: MetaDataField): String? = data[field] as? String
        private fun getLong(field: MetaDataField): Long? = data[field] as? Long
        private fun getInt(field: MetaDataField): Int? = data[field] as? Int
    }

    private class QueryFieldConfig(
        val column: String,
        val read: (Cursor, Int) -> Any?,
    )

    private val queryFieldConfigMap =
        EnumMap<MetaDataField, QueryFieldConfig>(MetaDataField::class.java).apply {
            put(
                MetaDataField.DISPLAY_NAME,
                QueryFieldConfig(OpenableColumns.DISPLAY_NAME) { c, i -> c.getString(i) })
            put(
                MetaDataField.FILE_SIZE,
                QueryFieldConfig(OpenableColumns.SIZE) { c, i -> c.getLong(i) })
            put(
                MetaDataField.MIME_TYPE,
                QueryFieldConfig(MediaStore.MediaColumns.MIME_TYPE) { c, i -> c.getString(i) })
            put(
                MetaDataField.DURATION,
                QueryFieldConfig(MediaStore.MediaColumns.DURATION) { c, i -> c.getLong(i) })
            put(
                MetaDataField.WIDTH,
                QueryFieldConfig(MediaStore.MediaColumns.WIDTH) { c, i -> c.getInt(i) })
            put(
                MetaDataField.HEIGHT,
                QueryFieldConfig(MediaStore.MediaColumns.HEIGHT) { c, i -> c.getInt(i) })
        }

    /**
     * batch Metadata with query providerResolver
     * No thrownable => Swallow all exceptions and return empty.
     */
    private fun syncQueryMultipleMetadataOnUriOrNull(
        context: Context,
        uri: Uri,
        fields: Set<MetaDataField>,
    ): MetadataResult {
        if (fields.isEmpty()) {
            return MetadataResult(EnumMap(MetaDataField::class.java))
        }

        val targetFields = fields.mapNotNull { field ->
            val config = queryFieldConfigMap[field]
            if (config != null) field to config else null
        }

        if (targetFields.isEmpty()) {
            return MetadataResult(EnumMap(MetaDataField::class.java))
        }

        val projection = targetFields.map { it.second.column }
            .distinct()
            .toTypedArray()
        val resultMap = EnumMap<MetaDataField, Any>(MetaDataField::class.java)

        try {
            context.contentResolver.query(uri, projection, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        for ((field, config) in targetFields) {
                            val index = cursor.getColumnIndex(config.column)
                            if (index != -1 && !cursor.isNull(index)) {
                                config.read(cursor, index)
                                    ?.let { value ->
                                        resultMap[field] = value
                                    }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Timber.i(e, "Failed to query metadata by contentResolver")
            // pass through, leaves unfinished field as null result
        }

        return MetadataResult(resultMap)
    }
}