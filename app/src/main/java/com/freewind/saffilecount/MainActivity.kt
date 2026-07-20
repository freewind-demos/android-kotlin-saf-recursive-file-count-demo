package com.freewind.saffilecount

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var selectedTreeUri by remember { mutableStateOf<Uri?>(null) }
    // 当前事件完整文案（可换行，不截断）
    var progressText by remember { mutableStateOf("") }
    // 全部事件日志（含耗时）
    var logText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("请先选择目录，再选一种计数方式。") }
    var isScanning by remember { mutableStateOf(false) }
    val logScroll = rememberScrollState()

    LaunchedEffect(logText) {
        if (logText.isNotEmpty()) {
            logScroll.scrollTo(logScroll.maxValue)
        }
    }

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            resultText = "未选择目录。"
            return@rememberLauncherForActivityResult
        }
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
        logText = ""
        resultText = "已选目录：\n$uri"
    }

    fun runCount(
        label: String,
        counter: (treeUri: Uri, onEvent: (String) -> Unit) -> Int,
    ) {
        val treeUri = selectedTreeUri ?: return
        isScanning = true
        progressText = "准备 $label …"
        logText = ""
        resultText = ""
        scope.launch {
            try {
                val total = withContext(Dispatchers.IO) {
                    counter(treeUri) { message ->
                        // 当前块完整显示；同时追加到信息区
                        scope.launch(Dispatchers.Main.immediate) {
                            progressText = message
                            logText = if (logText.isEmpty()) message else "$logText\n$message"
                        }
                    }
                }
                progressText = "完成  文件数量=$total"
                resultText = "$label\n文件数量：$total"
            } catch (error: Exception) {
                progressText = "失败：${error.message ?: error.javaClass.simpleName}"
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
            text = "文件相关 API 各记耗时；大操作含小操作时两边都记。上方当前事件完整显示；下方全部日志可滚。",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { openTreeLauncher.launch(null) }) {
            Text("选择目录")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedTreeUri != null && !isScanning,
            onClick = {
                runCount("DocumentFile.listFiles（批式）") { treeUri, onEvent ->
                    DocumentFileBatchCounter.countFiles(activity, treeUri, onEvent)
                }
            },
        ) {
            Text("DocumentFile.listFiles（批式）")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedTreeUri != null && !isScanning,
            onClick = {
                runCount("ContentResolver.query（流式）") { treeUri, onEvent ->
                    DocumentsContractStreamCounter.countFiles(activity, treeUri, onEvent)
                }
            },
        ) {
            Text("ContentResolver.query（流式）")
        }
        // 当前事件：完整显示，可换行，不 maxLines 截断
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            Text(
                text = progressText.ifEmpty { "（当前事件）" },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        // 全部过程日志：固定高度可滚
        Text(
            text = logText.ifEmpty { "（过程日志：API + 耗时 ms）" },
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(8.dp)
                .verticalScroll(logScroll),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Text(
            text = resultText,
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
        )
    }
}
