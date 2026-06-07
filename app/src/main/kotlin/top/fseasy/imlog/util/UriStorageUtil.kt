package top.fseasy.imlog.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever

/**
 * Metadata from contentResolver.Query results.
 * @property displayName stable as it's a general field in the contentResolver
 * @property fileSize stable
 */
data class ContentResolverQueriedResult(
    val displayName: String? = null, // stable
    val fileSize: Long? = null, // stable
    val mimeType: String? = null,
    val duration: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)

interface ContentResolverQueryField<T> {
    val column: String
    fun readAndApply(
        cursor: Cursor,
        current: ContentResolverQueriedResult,
    ): ContentResolverQueriedResult

    companion object {
        fun <T> create(
            column: String,
            extractor: (Cursor, Int) -> T?, // each field could be null
            updater: (ContentResolverQueriedResult, T) -> ContentResolverQueriedResult,
        ): ContentResolverQueryField<T> = object : ContentResolverQueryField<T> {
            override val column = column
            override fun readAndApply(
                cursor: Cursor,
                current: ContentResolverQueriedResult,
            ): ContentResolverQueriedResult {
                val index = cursor.getColumnIndex(column)
                if (index == -1) return current
                if (cursor.isNull(index)) return current
                val value = extractor(cursor, index)
                return if (value != null) updater(current, value) else current
            }
        }
    }
}

object MediaFields {
    val DISPLAY_NAME = ContentResolverQueryField.create(
        OpenableColumns.DISPLAY_NAME,
        { c, i -> c.getString(i) },
        { m, v -> m.copy(displayName = v) })

    val FILE_SIZE = ContentResolverQueryField.create(
        OpenableColumns.SIZE,
        { c, i -> c.getLong(i) },
        { m, v -> m.copy(fileSize = v) })

    val MIME_TYPE = ContentResolverQueryField.create(
        MediaStore.MediaColumns.MIME_TYPE,
        { c, i -> c.getString(i) },
        { m, v -> m.copy(mimeType = v) })

    val DURATION = ContentResolverQueryField.create(
        MediaStore.MediaColumns.DURATION,
        { c, i -> c.getLong(i) },
        { m, v -> m.copy(duration = v) })

    val WIDTH = ContentResolverQueryField.create(
        MediaStore.MediaColumns.WIDTH,
        { c, i -> c.getInt(i) },
        { m, v -> m.copy(width = v) })

    val HEIGHT = ContentResolverQueryField.create(
        MediaStore.MediaColumns.HEIGHT,
        { c, i -> c.getInt(i) },
        { m, v -> m.copy(height = v) })

}

data class Dimensions(val width: Int, val height: Int)

data class MediaMetadataRetrieverResult(
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null,
)

enum class MediaMetadataRetrieverKey(val code: int) {
    VIDEO_WIDTH(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), VIDEO_HEIGHT(
        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
    ),
    DURATION(MediaMetadataRetriever.METADATA_KEY_DURATION)
}

sealed interface FindFileResult {
    data class Success(val uri: Uri) : FindFileResult
    data object NotFound : FindFileResult
    sealed interface Error : FindFileResult {
        val cause: Throwable

        data class PermissionDenied(override val cause: SecurityException) : Error
        data class Unexpected(override val cause: Throwable) : Error
    }
}

enum class FileWriteMode(val value: String) {
    WRITE_TRUNCATE("wt"), WRITE_APPEND("wa"),
}

sealed interface FileCopyResult {
    data class Success(val bytesCopied: Long) : FileCopyResult

    sealed interface Error : FileCopyResult {
        val cause: Throwable

        // Source related errors
        data class SrcPermissionDenied(override val cause: SecurityException) : Error
        data class SrcNotFound(override val cause: FileNotFoundException) : Error
        data class SrcOpenUnexpected(override val cause: Exception) : Error

        // Target related errors
        data class TgtPermissionDenied(override val cause: SecurityException) : Error
        data class TgtNotFound(override val cause: FileNotFoundException) : Error
        data class TgtOpenUnexpected(override val cause: Exception) : Error

        // Copy process errors
        data class CopyIOError(override val cause: IOException, val bytesTransferred: Long = 0) :
            Error

        data class CopyUnexpected(override val cause: Throwable) : Error

        // Helper
        fun isRecoverable(): Boolean = when (this) {
            is SrcPermissionDenied -> true
            is SrcNotFound -> false
            is SrcOpenUnexpected -> true
            is TgtPermissionDenied -> true
            is TgtNotFound -> false
            is TgtOpenUnexpected -> true
            is CopyIOError -> true
            is CopyUnexpected -> true
        }
    }
}

object UriStorageUtil {

    /**
     * A combined contextResolver Query to get multiple metadata in one query.
     * STABLE result expectation Fields: DISPLAY_NAME, SIZE
     */
    fun resolveMetadata(
        context: Context,
        uri: Uri,
        fields: List<ContentResolverQueryField<*>>,
    ): ContentResolverQueriedResult {
        // 提取所有要查询的列名
        val projection = fields.map { it.column }
            .distinct()
            .toTypedArray()
        var fileMeta = ContentResolverQueriedResult()
        return context.contentResolver.query(uri, projection, null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use fileMeta

                for (field in fields) {
                    fileMeta = field.readAndApply(cursor, fileMeta)
                }
                fileMeta
            } ?: fileMeta
    }

    /**
     * More robust way compared to metadata.mimeType
     * Will always try to get a mime type on the uri (SAF or MediaStore)
     */
    suspend fun getMimeTypeFallback(context: Context, uri: Uri): String {
        var mimeType = context.contentResolver.getType(uri)
        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (!extension.isNullOrEmpty()) {
                mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.lowercase())
            }
        }
        return mimeType ?: "application/octet-stream"
    }

    /**
     * use This only when metadata.displayName is null
     */
    fun getFilenameFallback(uri: Uri): String? {
        // Uri.lastPathSegment could be null.
        return uri.lastPathSegment?.let { Uri.decode(it) }
    }

    // 仅读取图片边界，不解码图片本身，性能极高
    fun getImageDimensionsFallback(context: Context, uri: Uri): Dimensions? {
        return try {
            context.contentResolver.openInputStream(uri)
                ?.use { stream ->
                    val options = BitmapFactory.Options()
                        .apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    Dimensions(options.outWidth, options.outHeight)
                }
        } catch (e: Exception) {
            null
        }
    }

    fun getAudioDurationFallback(context: Context, uri: Uri): Long? = callMediaMetadataRetriever(
        context, uri, listOf(
            MediaMetadataRetrieverKey.DURATION
        )
    )?.duration

    fun getVideo3DimensionsFallback(context: Context, uri: Uri): MediaMetadataRetrieverResult? =
        callMediaMetadataRetriever(
            context, uri, listOf(
                MediaMetadataRetrieverKey.VIDEO_WIDTH,
                MediaMetadataRetrieverKey.VIDEO_HEIGHT,
                MediaMetadataRetrieverKey.DURATION,
            )
        )

    // 使用 MediaMetadataRetriever 获取视频宽高
    private fun callMediaMetadataRetriever(
        context: Context,
        uri: Uri,
        fields: List<MediaMetadataRetrieverKey>,
    ): MediaMetadataRetrieverResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            var result = MediaMetadataRetrieverResult()
            for (field in fields) {
                val value = retriever.extractMetadata(field.code)
                result = when (field) {
                    MediaMetadataRetrieverKey.VIDEO_WIDTH -> result.copy(
                        width = value?.toIntOrNull()
                            ?.takeIf { it > 0 })

                    MediaMetadataRetrieverKey.VIDEO_HEIGHT -> result.copy(
                        height = value?.toIntOrNull()
                            ?.takeIf { it > 0 })

                    MediaMetadataRetrieverKey.DURATION -> result.copy(
                        duration = value?.toLongOrNull()
                            ?.takeIf { it > 0L })
                }
            }
            result
        } catch (e: Exception) {
            Timber.d(e, "MediaMetadataRetriever extracting get exception")
            null
        } finally {
            retriever.release()
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
                    FileCopyResult.Success(bytesCopied)
                }
            } catch (e: IOException) {
                FileCopyResult.Error.CopyIOError(e)
            } catch (e: Exception) {
                FileCopyResult.Error.CopyUnexpected(e)
            }
        }
    }

    fun isSAFUriValid(context: Context, uri: Uri): Boolean {
        return try {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            context.contentResolver.query(uri, projection, null, null, null)
                ?.use { cursor ->
                    cursor.count > 0
                } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * find for given subDirs.
     * run in IO coroutine
     */
    suspend fun findSAFFileUri(
        context: Context,
        rootTreeUri: Uri,
        relativePathSegments: List<String>,
    ): FindFileResult = resolveSAFFileUri(
        context = context,
        rootTreeUri = rootTreeUri,
        relativePathSegments = relativePathSegments,
        mimeType = "plain/text", // a dummy value
        createIfMissing = false,
    )

    /**
     * find or create uri for given subDirs.
     * run in IO coroutine
     */
    suspend fun ensureSAFFileUri(
        context: Context,
        rootTreeUri: Uri,
        relativePathSegments: List<String>,
        mimeType: String,
    ): FindFileResult = resolveSAFFileUri(
        context = context,
        rootTreeUri = rootTreeUri,
        relativePathSegments = relativePathSegments,
        mimeType = mimeType,
        createIfMissing = true,
    )

    // Used for SAF resolve
    private val uriCache = LruCache<String, Uri>(100)

    /**
     * @param relativePathSegments: [dir1, ..., file]
     */
    private suspend fun resolveSAFFileUri(
        context: Context,
        rootTreeUri: Uri,
        relativePathSegments: List<String>,
        mimeType: String,
        createIfMissing: Boolean,
    ): FindFileResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        try {
            // 1. 初始化根目录 Uri
            var currentUri = DocumentsContract.buildDocumentUriUsingTree(
                rootTreeUri, DocumentsContract.getTreeDocumentId(rootTreeUri)
            )
            var startIndex = 0

            // 2. 利用 LRU 缓存：从最深处（文件）向外（根目录）逆向查找
            // 只要找到一个未失效的最深层缓存，就可以跳过前面的所有层级查找
            for (i in relativePathSegments.indices.reversed()) {
                val key = getCacheKey(rootTreeUri, relativePathSegments.subList(0, i + 1))
                val cachedUri = uriCache[key]

                if (cachedUri != null) {
                    if (isSAFUriValid(context, cachedUri)) {
                        currentUri = cachedUri
                        startIndex = i + 1
                        break
                    } else {
                        // 缓存已失效（如用户手动删除了该目录/文件），及时清理
                        uriCache.remove(key)
                    }
                }
            }

            // 3. 如果最深层路径（即文件本身）已经命中且有效，直接返回
            if (startIndex == relativePathSegments.size) {
                return@withContext FindFileResult.Success(currentUri)
            }

            // 4. 从找到的最近有效节点开始，向下级继续查找或创建
            for (i in startIndex until relativePathSegments.size) {
                val nodeName = relativePathSegments[i]
                val isFile = (i == relativePathSegments.lastIndex) // 判断当前节点是不是最后的文件

                val childUri = findChild(context, currentUri, nodeName)

                currentUri = when {
                    childUri != null -> childUri

                    createIfMissing -> {
                        val targetMimeType =
                            if (isFile) mimeType else DocumentsContract.Document.MIME_TYPE_DIR
                        DocumentsContract.createDocument(
                            resolver, currentUri, targetMimeType, nodeName
                        ) ?: return@withContext FindFileResult.Error.Unexpected(
                            IllegalStateException("Failed to create ${if (isFile) "file" else "directory"}: $nodeName")
                        )
                    }

                    else -> {
                        // 没找到且不允许创建，直接返回 NotFound
                        return@withContext FindFileResult.NotFound
                    }
                }

                // 将新找到或新创建的 Uri 加入缓存
                val key = getCacheKey(rootTreeUri, relativePathSegments.subList(0, i + 1))
                uriCache.put(key, currentUri)
            }

            FindFileResult.Success(currentUri)

        } catch (e: SecurityException) {
            Timber.e(e, "Resolve file get permission denied")
            FindFileResult.Error.PermissionDenied(e)
        } catch (e: Exception) {
            Timber.e(e, "Resolve file get unknown exception")
            FindFileResult.Error.Unexpected(e)
        }
    }

    private fun findChild(
        context: Context,
        parentUri: Uri,
        displayName: String,
    ): Uri? {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri, DocumentsContract.getDocumentId(parentUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        return try {
            resolver.query(childrenUri, projection, null, null, null)
                ?.use { cursor ->
                    val idIndex =
                        cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex =
                        cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                    if (idIndex != -1 && nameIndex != -1) {
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == displayName) {
                                return DocumentsContract.buildDocumentUriUsingTree(
                                    parentUri, cursor.getString(idIndex)
                                )
                            }
                        }
                    }
                    null
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to find child: $displayName")
            null
        }
    }

    /**
     * 生成 LRU 缓存的唯一标识 Key
     * 格式示例: "content://com.android.externalstorage...::IMLog/2026-06/backup.txt"
     */
    private fun getCacheKey(treeUri: Uri, pathParts: List<String>): String {
        return "$treeUri::${pathParts.joinToString("/")}"
    }
}

