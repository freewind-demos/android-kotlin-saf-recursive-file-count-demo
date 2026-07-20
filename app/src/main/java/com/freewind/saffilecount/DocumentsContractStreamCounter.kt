package com.freewind.saffilecount

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * 流式扫描：ContentResolver.query + Cursor.moveToNext，
 * 每拿到一行就能访问该条目 → Progress 报当前文件名；结束返回总数。
 */
object DocumentsContractStreamCounter {

    // 列目录只需 id / 名 / mime；mime 用来区分目录与文件
    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )

    /**
     * 递归统计 treeUri 下全部文件（不含目录本身）。
     * @return 文件总数
     */
    fun countFiles(
        context: Context,
        treeUri: Uri,
        onProgress: (String) -> Unit,
    ): Int {
        val contentResolver = context.contentResolver
        // tree 根 documentId
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

        var totalFiles = 0

        fun walk(parentDocumentId: String, dirLabel: String) {
            // 构造「某目录下子文档」查询 URI
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocumentId,
            )

            // query 失败返回 null → 直接抛，禁止当空目录
            val cursor = contentResolver.query(childrenUri, projection, null, null, null)
                ?: throw IllegalStateException("ContentResolver.query 返回 null：$dirLabel")

            // 待递归的子目录 (documentId, 路径标签)
            val subDirs = mutableListOf<Pair<String, String>>()

            cursor.use { rows ->
                val idIndex = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                // 流式：每 moveToNext 一次就能访问当前行
                while (rows.moveToNext()) {
                    val documentId = rows.getString(idIndex)
                        ?: throw IllegalStateException("documentId 为 null：$dirLabel")
                    val displayName = rows.getString(nameIndex)
                        ?: throw IllegalStateException("displayName 为 null：id=$documentId")
                    val mimeType = rows.getString(mimeIndex)
                    val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                    val childLabel = if (dirLabel == "/") displayName else "$dirLabel/$displayName"

                    if (isDirectory) {
                        // 目录：Progress 标一下当前扫到的目录名，稍后递归
                        onProgress("目录 $childLabel")
                        subDirs += documentId to childLabel
                    } else {
                        // 文件：Progress 写当前文件名
                        totalFiles += 1
                        onProgress("文件 $childLabel")
                    }
                }
            }

            // cursor 用完后再进子目录，避免嵌套 query 占住同一 connection
            for ((documentId, label) in subDirs) {
                walk(documentId, label)
            }
        }

        walk(rootDocumentId, "/")
        return totalFiles
    }
}
