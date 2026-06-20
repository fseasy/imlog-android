package top.fseasy.imlog.data.util

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

enum class MediaMetadataRetrieverKey(val code: Int) {
    VIDEO_WIDTH(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), VIDEO_HEIGHT(
        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
    ),
    DURATION(MediaMetadataRetriever.METADATA_KEY_DURATION)
}

sealed interface FindOrCreateFileUriResult {
    data class Success(val uri: Uri) : FindOrCreateFileUriResult

    /**
     * Logically only it exists when it's in find-only mode (call `findSAFFileUri`)
     */
    data object NotFound : FindOrCreateFileUriResult
    sealed interface Error : FindOrCreateFileUriResult {
        val cause: Throwable

        data class PermissionDenied(override val cause: SecurityException) : Error
        data class Unexpected(override val cause: Throwable) : Error
    }
}

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
     *
     * RUN IN **IO thread**.
     *
     * @throws Exception
     */
    suspend fun resolveMetadata(
        context: Context,
        uri: Uri,
        fields: List<ContentResolverQueryField<*>>,
    ): ContentResolverQueriedResult = withContext(Dispatchers.IO) {
        // 提取所有要查询的列名
        val projection = fields.map { it.column }
            .distinct()
            .toTypedArray()
        var fileMeta = ContentResolverQueriedResult()
        context.contentResolver.query(uri, projection, null, null, null)
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
     * SYNC.
     */
    fun getMimeTypeFallback(context: Context, uri: Uri): String {
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

    /** A quick util to get path's last part name (filepath -> filename, dirpath -> dirname)
     * 1. get from content resolver metadata 2. fallback
     * If you need to get multiple fields of metadata, you can use `resolveMetadata` for better perf.
     *
     * RUN IN **IO** thread.
     *
     * @throws Exception
     */
    suspend fun getDisplayNameWithFallback(context: Context, uri: Uri): String? {
        return resolveMetadata(
            context, uri = uri, fields = listOf(MediaFields.DISPLAY_NAME)
        ).displayName ?: getDisplayNameFallback(uri)
    }

    /** FALLBACK: Get path's last part name (filepath -> filename, dirpath -> dirname)
     * use This only when metadata.displayName is null
     */
    fun getDisplayNameFallback(uri: Uri): String? {
        // Uri.lastPathSegment could be null.
        return uri.lastPathSegment?.let { Uri.decode(it) }
    }

    /**
     * Read the image boundary without decoding the whole image. It's high efficiency.
     * Sync BLOCKING IO.
     */
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
            Timber.i(e, "Failed to get image dimension by decodeStream")
            null
        }
    }

    fun getAudioDurationFallback(context: Context, uri: Uri): Long? = callMediaMetadataRetriever(
        context, uri, listOf(
            MediaMetadataRetrieverKey.DURATION
        )
    )?.duration

    fun getVideo3DimensionsFallback(
        context: Context,
        uri: Uri,
    ): MediaMetadataRetrieverResult? = callMediaMetadataRetriever(
        context, uri, listOf(
            MediaMetadataRetrieverKey.VIDEO_WIDTH,
            MediaMetadataRetrieverKey.VIDEO_HEIGHT,
            MediaMetadataRetrieverKey.DURATION,
        )
    )

    /**
     * Use MediaMetadataRetriever to Get specific fields
     * NOTE: it's sync blocking io. Use io coroutine
     */
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
     * Run in IO thread.
     * No exception thrown.
     */
    suspend fun writeData(
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
     * find for given subDirs / file paths. It **can't** distinguish if it's a file/dir.
     * run in IO thread
     */
    suspend fun findSAFUri(
        context: Context,
        rootTreeUri: Uri,
        relativePathSegments: List<String>,
    ): FindOrCreateFileUriResult = resolveSAFUri(
        context = context,
        rootTreeUri = rootTreeUri,
        relativePathSegments = relativePathSegments,
        isTargetDirectory = false, // a dummy value => it actually won't check if it's dir/file
        fileMimeType = "plain/text", // a dummy value
        createIfMissing = false,
    )

    /**
     * find or create uri for the given relative file path.
     * run in IO coroutine
     * @param relativePathSegments last segment should be the file name.
     * @param fileMimeType should be the right MimeType for the file
     */
    suspend fun ensureSAFFileUri(
        context: Context,
        rootTreeUri: Uri,
        relativePathSegments: List<String>,
        fileMimeType: String,
    ): FindOrCreateFileUriResult = resolveSAFUri(
        context = context,
        rootTreeUri = rootTreeUri,
        relativePathSegments = relativePathSegments,
        isTargetDirectory = false,
        fileMimeType = fileMimeType,
        createIfMissing = true,
    )

    /**
     * find or create uri for given sub Dirs.
     * run in IO coroutine
     * @param relativePathSegments it should be the dir segments
     */
    suspend fun ensureSAFDirectoryUri(
        context: Context,
        rootTreeUri: Uri,
        relativePathSegments: List<String>,
    ): FindOrCreateFileUriResult = resolveSAFUri(
        context = context,
        rootTreeUri = rootTreeUri,
        relativePathSegments = relativePathSegments,
        isTargetDirectory = true,
        fileMimeType = null,
        createIfMissing = true,
    )

    // Used for SAF resolve
    private val uriCache = LruCache<String, Uri>(100)

    /**
     * @param relativePathSegments: [dir1, dir2, ...] or [dir1, ..., file]
     */
    private suspend fun resolveSAFUri(
        context: Context,
        rootTreeUri: Uri,
        relativePathSegments: List<String>,
        isTargetDirectory: Boolean,
        fileMimeType: String?,
        createIfMissing: Boolean,
    ): FindOrCreateFileUriResult = withContext(Dispatchers.IO) {
        if (!isTargetDirectory && fileMimeType == null) {
            return@withContext FindOrCreateFileUriResult.Error.Unexpected(
                IllegalArgumentException("Target is file while mimeType hasn't been assigned")
            )
        }

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
                return@withContext FindOrCreateFileUriResult.Success(currentUri)
            }

            // 4. 从找到的最近有效节点开始，向下级继续查找或创建
            for (i in startIndex until relativePathSegments.size) {
                val nodeName = relativePathSegments[i]

                val childUri = findChild(context, currentUri, nodeName)

                currentUri = when {
                    childUri != null -> childUri

                    createIfMissing -> {
                        val isFile = (i == relativePathSegments.lastIndex) && !isTargetDirectory

                        val targetMimeType = if (isFile) {
                            fileMimeType!!
                        } else {
                            DocumentsContract.Document.MIME_TYPE_DIR
                        }
                        DocumentsContract.createDocument(
                            resolver, currentUri, targetMimeType, nodeName
                        ) ?: return@withContext FindOrCreateFileUriResult.Error.Unexpected(
                            IllegalStateException("Failed to create ${if (isFile) "file" else "directory"}: $nodeName")
                        )
                    }

                    else -> {
                        // 没找到且不允许创建，直接返回 NotFound
                        return@withContext FindOrCreateFileUriResult.NotFound
                    }
                }

                // 将新找到或新创建的 Uri 加入缓存
                val key = getCacheKey(rootTreeUri, relativePathSegments.subList(0, i + 1))
                uriCache.put(key, currentUri)
            }

            FindOrCreateFileUriResult.Success(currentUri)

        } catch (e: SecurityException) {
            Timber.e(e, "Resolve file get permission denied")
            FindOrCreateFileUriResult.Error.PermissionDenied(e)
        } catch (e: Exception) {
            Timber.e(e, "Resolve file get unknown exception")
            FindOrCreateFileUriResult.Error.Unexpected(e)
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

