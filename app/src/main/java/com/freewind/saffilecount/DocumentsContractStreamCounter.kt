package com.freewind.saffilecount

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract

/**
 * 流式扫描：ContentResolver.query + Cursor.moveToNext。
 * query 等文件相关 API 单独计时；目录级大操作再报总耗时。
 * SIZE 来自同 cursor 行，不另开访问 API。
 */
object DocumentsContractStreamCounter {

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    /**
     * @param onEvent 过程事件（同时进 Progress 当前块 + 信息区追加）
     * @return 文件总数
     */
    fun countFiles(
        context: Context,
        treeUri: Uri,
        onEvent: (String) -> Unit,
    ): Int {
        val contentResolver = context.contentResolver

        val idStart = SystemClock.elapsedRealtime()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        onEvent("getTreeDocumentId()  ${elapsedMs(idStart)}ms  id=$rootDocumentId")

        var totalFiles = 0

        fun walk(parentDocumentId: String, dirLabel: String) {
            val dirStart = SystemClock.elapsedRealtime()
            onEvent("开始扫描目录 $dirLabel")

            val buildStart = SystemClock.elapsedRealtime()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocumentId,
            )
            onEvent("  buildChildDocumentsUriUsingTree()  ${elapsedMs(buildStart)}ms")

            val queryStart = SystemClock.elapsedRealtime()
            val cursor = contentResolver.query(childrenUri, projection, null, null, null)
                ?: throw IllegalStateException("ContentResolver.query 返回 null：$dirLabel")
            val queryMs = elapsedMs(queryStart)

            val subDirs = mutableListOf<Pair<String, String>>()
            var rowCount = 0
            var fileInDir = 0

            val cursorStart = SystemClock.elapsedRealtime()
            cursor.use { rows ->
                val idIndex = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                onEvent("  ContentResolver.query()  ${queryMs}ms  getCount=${rows.count}")

                while (rows.moveToNext()) {
                    rowCount += 1
                    val documentId = rows.getString(idIndex)
                        ?: throw IllegalStateException("documentId 为 null：$dirLabel")
                    val displayName = rows.getString(nameIndex)
                        ?: throw IllegalStateException("displayName 为 null：id=$documentId")
                    val mimeType = rows.getString(mimeIndex)
                    val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                    val childLabel = if (dirLabel == "/") displayName else "$dirLabel/$displayName"

                    if (isDirectory) {
                        onEvent("  子目录 $childLabel  (cursor 行，无另开访问)")
                        subDirs += documentId to childLabel
                    } else {
                        val sizeBytes = if (rows.isNull(sizeIndex)) 0L else rows.getLong(sizeIndex)
                        fileInDir += 1
                        totalFiles += 1
                        onEvent(
                            "  文件 $childLabel  size=$sizeBytes" +
                                "  (COLUMN_SIZE 同行，无另开访问)",
                        )
                    }
                }
            }
            onEvent(
                "  cursor 遍历  ${elapsedMs(cursorStart)}ms  → $rowCount 行" +
                    "  本层文件 $fileInDir",
            )
            onEvent(
                "目录 $dirLabel 完成  ${elapsedMs(dirStart)}ms" +
                    "  子目录 ${subDirs.size}",
            )

            for ((documentId, label) in subDirs) {
                walk(documentId, label)
            }
        }

        val treeStart = SystemClock.elapsedRealtime()
        walk(rootDocumentId, "/")
        onEvent("整树扫描完成  ${elapsedMs(treeStart)}ms  文件总数=$totalFiles")
        return totalFiles
    }

    private fun elapsedMs(start: Long): Long = SystemClock.elapsedRealtime() - start
}
