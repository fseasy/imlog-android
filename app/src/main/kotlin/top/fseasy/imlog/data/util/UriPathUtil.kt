package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed interface FindOrCreateFileUriResult {
    data class Success(val uri: Uri) : FindOrCreateFileUriResult

    /**
     * Logically, it exists only when it's in find-only mode (call `findSAFFileUri`)
     */
    data object NotFound : FindOrCreateFileUriResult
    sealed interface Error : FindOrCreateFileUriResult {
        val cause: Throwable

        data class PermissionDenied(override val cause: SecurityException) : Error
        data class Unexpected(override val cause: Throwable) : Error
    }
}

object UriPathUtil {

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

    private fun isSAFUriValid(context: Context, uri: Uri): Boolean {
        return try {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            context.contentResolver.query(uri, projection, null, null, null)
                ?.use { cursor ->
                    cursor.count > 0
                } ?: false
        } catch (e: Exception) {
            Timber.i(e, "Validate SAF uri failed: $uri")
            false
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

