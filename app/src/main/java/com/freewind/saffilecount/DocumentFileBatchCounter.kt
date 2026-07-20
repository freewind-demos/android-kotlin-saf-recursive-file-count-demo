package com.freewind.saffilecount

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * 批式扫描：DocumentFile.listFiles() 一次返回整目录子项数组，
 * 过程中无法逐文件回调 → Progress 只报「当前目录」与「该目录拿到多少项」。
 */
object DocumentFileBatchCounter {

    /**
     * 递归统计 treeUri 下全部文件（不含目录本身）。
     * @return 文件总数
     */
    fun countFiles(
        context: Context,
        treeUri: Uri,
        onProgress: (String) -> Unit,
    ): Int {
        // 从 tree URI 打开根目录 DocumentFile
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("DocumentFile.fromTreeUri() 无法打开目录: $treeUri")

        // 累计整棵树的文件数
        var totalFiles = 0

        // 深度优先走遍所有目录
        fun walk(dir: DocumentFile, dirLabel: String) {
            // 批式 API：先报「正在扫哪个目录」
            onProgress("扫描目录 $dirLabel …")

            // listFiles 阻塞到整目录子项一次返回
            val children = dir.listFiles()

            // 本目录内文件数 / 子目录数（仅本层，非递归）
            var fileInDir = 0
            val subDirs = mutableListOf<Pair<DocumentFile, String>>()

            for (child in children) {
                when {
                    child.isDirectory -> {
                        // 子目录名；无名则报错，禁止 silent skip
                        val name = child.name
                            ?: throw IllegalStateException("子目录无 displayName，uri=${child.uri}")
                        val childLabel = if (dirLabel == "/") name else "$dirLabel/$name"
                        subDirs += child to childLabel
                    }
                    child.isFile -> {
                        fileInDir += 1
                        totalFiles += 1
                    }
                    else -> {
                        // 既非目录也非文件 → 异常状态，必须可见
                        throw IllegalStateException(
                            "未知条目：isDirectory=false isFile=false uri=${child.uri}",
                        )
                    }
                }
            }

            // 批式 Progress：该目录 listFiles 完成后报本层拿到多少文件
            onProgress("目录 $dirLabel → ${children.size} 项，其中文件 $fileInDir")

            // 再递归进子目录
            for ((subDir, label) in subDirs) {
                walk(subDir, label)
            }
        }

        walk(root, "/")
        return totalFiles
    }
}
