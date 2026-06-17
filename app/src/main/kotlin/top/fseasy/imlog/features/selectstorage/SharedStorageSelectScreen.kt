package top.fseasy.imlog.features.selectstorage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.fseasy.imlog.util.FindOrCreateFileUriResult
import top.fseasy.imlog.util.UriStorageUtil

@Composable
fun SharedStorageSelectScreen() {
    val viewModel: SharedStorageSelectViewModel = hiltViewModel()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 状态：记录当前已授权的文件夹 Uri（若无则为 null）
    val selectedUri by viewModel.storageUri.collectAsState(initial = null)
    var isWriting by remember { mutableStateOf(false) }

    // 1. 注册 Compose 的 Activity 结果启动器，用于调起系统的“选择目录”界面
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // 关键一步：申请持久化的读写权限。这样用户选过一次后，下次打开 App 就不需要再选了
                val takeFlags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                // 保存到本地并更新 UI
                viewModel.selectStorageUri(uri)
                Toast.makeText(context, "目录授权成功！", Toast.LENGTH_SHORT)
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "授权失败: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    // 2. 界面布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 显示当前状态
        Text(
            text = if (selectedUri != null) "已绑定外部备份目录" else "尚未选择备份目录",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = selectedUri?.toString() ?: "请点击下方按钮选择（如 Documents 或 Download 目录）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 按钮一：选择文件夹
        Button(
            onClick = {
                // 启动文件夹选择器
                folderPickerLauncher.launch(null)
            }, modifier = Modifier.fillMaxWidth()
        ) {
            Text("选择并授权备份目录")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 按钮二：执行写入测试（只有在已授权且未在写入时可用）
        Button(
            onClick = {
                val currentUri = selectedUri ?: return@Button
                isWriting = true

                // 在协程中进行后台 I/O 写入，避免阻塞 UI 导致卡顿
                coroutineScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            val ensureResult = UriStorageUtil.ensureSAFFileUri(
                                context = context,
                                rootTreeUri = currentUri,
                                relativePathSegments = listOf("Backup", "log.txt"),
                                mimeType = "plain/text"
                            )

                            if (ensureResult is FindOrCreateFileUriResult.Success) {
                                val logContent =
                                    "备份时间: ${System.currentTimeMillis()}\n这是一条测试备份日志。\n"
                                UriStorageUtil.writeData(
                                    context,
                                    ensureResult.uri,
                                    logContent.toByteArray()
                                )
                                true
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                    }

                    isWriting = false
                    if (success) {
                        Toast.makeText(
                            context, "测试日志写入成功！可在你选择的目录下查看", Toast.LENGTH_LONG
                        )
                            .show()
                    } else {
                        Toast.makeText(context, "写入失败，请检查权限", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }, enabled = selectedUri != null && !isWriting, modifier = Modifier.fillMaxWidth()
        ) {
            if (isWriting) {
                CircularProgressIndicator(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("写入测试数据 (IMLog/Backup/log.txt)")
            }
        }
    }
}

// 辅助：用于控制进度条大小
@Composable
fun CircularProgressIndicator(size: Dp, color: Color) {
    Box(modifier = Modifier.size(size)) {
        CircularProgressIndicator(color = color, strokeWidth = 2.dp)
    }
}