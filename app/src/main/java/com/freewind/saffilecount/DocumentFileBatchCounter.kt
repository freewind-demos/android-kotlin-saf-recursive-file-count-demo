package com.freewind.saffilecount

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile

/**
 * 批式扫描：DocumentFile.listFiles() 一次返回整目录子项。
 * 每个文件相关 API 单独计时；目录级大操作再报总耗时。
 */
object DocumentFileBatchCounter {

    /**
     * @param onEvent 过程事件（同时进 Progress 当前块 + 信息区追加）
     * @return 文件总数
     */
    fun countFiles(
        context: Context,
        treeUri: Uri,
        onEvent: (String) -> Unit,
    ): Int {
        val openStart = SystemClock.elapsedRealtime()
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("DocumentFile.fromTreeUri() 无法打开目录: $treeUri")
        onEvent("fromTreeUri()  ${elapsedMs(openStart)}ms  uri=$treeUri")

        var totalFiles = 0

        fun walk(dir: DocumentFile, dirLabel: String) {
            val dirStart = SystemClock.elapsedRealtime()
            onEvent("开始扫描目录 $dirLabel")

            val listStart = SystemClock.elapsedRealtime()
            val children = dir.listFiles()
            onEvent("  listFiles()  ${elapsedMs(listStart)}ms  → ${children.size} 项")

            var fileInDir = 0
            val subDirs = mutableListOf<Pair<DocumentFile, String>>()

            for (child in children) {
                val isDirStart = SystemClock.elapsedRealtime()
                val isDirectory = child.isDirectory
                val isDirMs = elapsedMs(isDirStart)

                if (isDirectory) {
                    val nameStart = SystemClock.elapsedRealtime()
                    val name = child.name
                        ?: throw IllegalStateException("子目录无 displayName，uri=${child.uri}")
                    val nameMs = elapsedMs(nameStart)
                    val childLabel = if (dirLabel == "/") name else "$dirLabel/$name"
                    onEvent(
                        "  子目录 $childLabel  isDirectory() ${isDirMs}ms  name() ${nameMs}ms",
                    )
                    subDirs += child to childLabel
                    continue
                }

                val isFileStart = SystemClock.elapsedRealtime()
                val isFile = child.isFile
                val isFileMs = elapsedMs(isFileStart)
                if (!isFile) {
                    throw IllegalStateException(
                        "未知条目：isDirectory=false isFile=false uri=${child.uri}",
                    )
                }

                val nameStart = SystemClock.elapsedRealtime()
                val name = child.name
                    ?: throw IllegalStateException("文件无 displayName，uri=${child.uri}")
                val nameMs = elapsedMs(nameStart)

                val lengthStart = SystemClock.elapsedRealtime()
                val sizeBytes = child.length()
                val lengthMs = elapsedMs(lengthStart)

                val childLabel = if (dirLabel == "/") name else "$dirLabel/$name"
                fileInDir += 1
                totalFiles += 1
                onEvent(
                    "  文件 $childLabel  size=$sizeBytes" +
                        "  isDirectory() ${isDirMs}ms" +
                        "  isFile() ${isFileMs}ms" +
                        "  name() ${nameMs}ms" +
                        "  length() ${lengthMs}ms",
                )
            }

            onEvent(
                "目录 $dirLabel 完成  ${elapsedMs(dirStart)}ms" +
                    "  本层文件 $fileInDir  子目录 ${subDirs.size}",
            )

            for ((subDir, label) in subDirs) {
                walk(subDir, label)
            }
        }

        val treeStart = SystemClock.elapsedRealtime()
        walk(root, "/")
        onEvent("整树扫描完成  ${elapsedMs(treeStart)}ms  文件总数=$totalFiles")
        return totalFiles
    }

    private fun elapsedMs(start: Long): Long = SystemClock.elapsedRealtime() - start
}
