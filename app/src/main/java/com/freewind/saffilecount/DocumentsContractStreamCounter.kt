package com.freewind.saffilecount

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * 流式扫描：ContentResolver.query + Cursor.moveToNext，
 * 每拿到一行就能访问该条目 → Progress 报当前文件名；结束返回总数。
 * SIZE 列在同一 cursor 行里，不算另开 API 访问该文件。
 */
object DocumentsContractStreamCounter {

    // id / 名 / mime / size；mime 区分目录与文件，size 直接进信息区
    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    /**
     * 递归统计 treeUri 下全部文件（不含目录本身）。
     * @param onFileListed 每认出一个文件就回调（path + sizeBytes），供信息区追加
     * @return 文件总数
     */
    fun countFiles(
        context: Context,
        treeUri: Uri,
        onProgress: (String) -> Unit,
        onFileListed: (path: String, sizeBytes: Long) -> Unit,
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
                val sizeIndex = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

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
                        // 文件：Progress 写当前文件名；size 来自同 cursor 行
                        val sizeBytes = if (rows.isNull(sizeIndex)) 0L else rows.getLong(sizeIndex)
                        totalFiles += 1
                        onProgress("文件 $childLabel")
                        onFileListed(childLabel, sizeBytes)
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
