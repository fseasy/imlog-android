package top.fseasy.imlog.data.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import timber.log.Timber
import java.io.FileOutputStream

object FastSAFManager {
    /**
     * 在用户授权的 TreeUri 下，创建多级子目录并创建文件
     * @param treeUri 用户授权的根目录 Uri
     * @param subDirs 多级子目录，例如: listOf("IMLog", "2026-06")
     * @param fileName 文件名，例如: "backup.txt"
     * @param mimeType 文件类型
     * @return 创建或找到的文件 Uri，若失败则返回 null
     */
    fun getOrCreateFileUri(
        context: Context,
        treeUri: Uri,
        subDirs: List<String>,
        fileName: String,
        mimeType: String = "text/plain",
    ): Uri? {
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
                    ) ?: return null
                    currentUri = newDirUri
                    currentDocId = DocumentsContract.getDocumentId(currentUri)
                }
            }

            // 3. 在最后一级目录下查找或创建文件
            return findChild(context, currentUri, fileName) ?: DocumentsContract.createDocument(
                resolver, currentUri, mimeType, fileName
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get or create file uri")
            return null
        }
    }

    /**
     * 高性能写入：直接通过 FileDescriptor 写入
     * @return 是否写入成功
     */
    fun writeData(context: Context, fileUri: Uri, content: ByteArray): Boolean {
        return try {
            // 使用 use 自动管理 ParcelFileDescriptor 的生命周期
            context.contentResolver.openFileDescriptor(fileUri, "wa")
                ?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                        outputStream.write(content)
                        outputStream.flush()
                    }
                }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to write data to uri: $fileUri")
            false
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