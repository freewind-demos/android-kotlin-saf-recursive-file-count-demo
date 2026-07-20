package com.freewind.saffilecount

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 入口：选 SAF 目录 → 两种递归计数方式对比 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FileCountScreen(activity = this@MainActivity)
                }
            }
        }
    }
}

@Composable
private fun FileCountScreen(activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    // 用户授权的 tree URI
    var selectedTreeUri by remember { mutableStateOf<Uri?>(null) }
    // 扫描中单行 Progress
    var progressText by remember { mutableStateOf("") }
    // 最终结果（文件数量）
    var resultText by remember { mutableStateOf("请先选择目录，再选一种计数方式。") }
    var isScanning by remember { mutableStateOf(false) }

    // SAF 选目录
    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            resultText = "未选择目录。"
            return@rememberLauncherForActivityResult
        }
        // 持久化读权限；失败必须可见
        runCatching {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            resultText = "目录权限保存失败：${error.message ?: error.javaClass.simpleName}"
            return@rememberLauncherForActivityResult
        }
        selectedTreeUri = uri
        progressText = ""
        resultText = "已选目录：\n$uri"
    }

    /** 在 IO 线程跑计数，主线程刷 Progress / 结果 */
    fun runCount(label: String, counter: (Uri, (String) -> Unit) -> Int) {
        val treeUri = selectedTreeUri ?: return
        isScanning = true
        progressText = "准备 $label …"
        resultText = ""
        scope.launch {
            try {
                val total = withContext(Dispatchers.IO) {
                    counter(treeUri) { message ->
                        // Progress 回调可能在 IO 线程 → 切主线程更新一行文案
                        scope.launch(Dispatchers.Main.immediate) {
                            progressText = message
                        }
                    }
                }
                progressText = "完成"
                resultText = "$label\n文件数量：$total"
            } catch (error: Exception) {
                progressText = "失败"
                resultText = "扫描失败：${error.message ?: error.javaClass.simpleName}"
            }
            isScanning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "android-kotlin-saf-recursive-file-count-demo",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "批式 listFiles：Progress=当前目录+本层数量；流式 query：Progress=当前文件名；结束显示总数。",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { openTreeLauncher.launch(null) }) {
            Text("选择目录")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedTreeUri != null && !isScanning,
            onClick = {
                runCount("DocumentFile.listFiles（批式）") { treeUri, onProgress ->
                    DocumentFileBatchCounter.countFiles(activity, treeUri, onProgress)
                }
            },
        ) {
            Text("DocumentFile.listFiles（批式）")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedTreeUri != null && !isScanning,
            onClick = {
                runCount("ContentResolver.query（流式）") { treeUri, onProgress ->
                    DocumentsContractStreamCounter.countFiles(activity, treeUri, onProgress)
                }
            },
        ) {
            Text("ContentResolver.query（流式）")
        }
        if (isScanning || progressText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                Text(
                    text = progressText.ifEmpty { "…" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = resultText,
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
        )
    }
}
