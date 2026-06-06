1. Ask about Android Jetpack Compose, SQLDelight

我在做一个以 IM 聊天形式承载的个人记录 app. app 进来是多个 Topics，每个 Topics 里面就是自己和别人发送的消息——记录内容。
技术栈是 Android Jetpack Compose + SQLDelight.

2. SAF create or find uri

object FileManager {
/**
* SAF: 在用户授权的 TreeUri 下，创建多级子目录并创建文件
* @param treeUri 用户授权的根目录 Uri
* @param subDirs 多级子目录，例如: listOf("IMLog", "2026-06")
* @param fileName 文件名，例如: "backup.txt"
* @param mimeType 文件类型
* @return 创建或找到的文件 Uri，若失败则返回 null
*/
suspend fun findOrCreateSAFFileUri(
context: Context,
treeUri: Uri,
subDirs: List<String>,
fileName: String,
mimeType: String = "text/plain",
): FindOrCreateFileResult = withContext(Dispatchers.IO) {
val resolver = context.contentResolver

        try {
            // 1. 获取根目录的 DocumentId
            var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            var currentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)

            // 2. 逐级查找或创建子目录
            for (dirName in subDirs) {
                val childUri = findChild(context, currentUri, dirName)
                if (childUri != null) {
                    currentUri = childUri
                    currentDocId = DocumentsContract.getDocumentId(currentUri)
                } else {
                    // 没找到则创建子目录
                    val newDirUri = DocumentsContract.createDocument(
                        resolver, currentUri, DocumentsContract.Document.MIME_TYPE_DIR, dirName
                    ) ?: return@withContext FindOrCreateFileResult.Error.Unexpected(
                        Exception("find child failed")
                    )
                    currentUri = newDirUri
                    currentDocId = DocumentsContract.getDocumentId(currentUri)
                }
            }

            val existingFile = findChild(context, currentUri, fileName)
            if (existingFile != null) {
                return@withContext FindOrCreateFileResult.Success(existingFile)
            }

            val newFile = DocumentsContract.createDocument(resolver, currentUri, mimeType, fileName)
                ?: return@withContext FindOrCreateFileResult.Error.Unexpected(
                    Exception("createDocument returned null for file: $fileName")
                )
            FindOrCreateFileResult.Success(newFile)

        } catch (e: SecurityException) {
            Timber.e(e, "Failed to get or create file uri due to permission issue")
            FindOrCreateFileResult.Error.PermissionDenied(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get or create file uri (not permission issue)")
            FindOrCreateFileResult.Error.Unexpected(e)
        }
    }


    /**
     * 辅助方法：快速在指定目录下查找子文件/子目录
     */
    private fun findChild(context: Context, parentUri: Uri, displayName: String): Uri? {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri, DocumentsContract.getDocumentId(parentUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        try {
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
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query children of uri: $parentUri")
        }
        return null
    }
}

sealed class FindOrCreateFileResult {
data class Success(val uri: Uri) : FindOrCreateFileResult()
sealed class Error : FindOrCreateFileResult() {
data class PermissionDenied(val cause: SecurityException) : Error()
data class Unexpected(val cause: Throwable) : Error()
}
}

enum class FileWriteMode(val value: String) {
WRITE_TRUNCATE("wt"), WRITE_APPEND("wa"),
}

---

这个同时 find + create 的逻辑是不是没啥意义？因为一般我只有 2 个独立的需求：
1. 找到相对路径下的 uri （重建）
2. 创建相对路径的 uri (新建）
   这个同时做 2 个事情的，感觉效率是不是比单独多 2 个事情低啊