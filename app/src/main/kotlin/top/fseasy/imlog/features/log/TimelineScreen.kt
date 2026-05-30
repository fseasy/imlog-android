package top.fseasy.imlog.features.log

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Topic
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    topicId: String,
    onBack: () -> Unit,
    onSettingsClick: (String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(topicId) {
        viewModel.loadTopic(topicId)
    }

    TimelineContent(
        uiState = uiState,
        onBack = onBack,
        onSettingsClick = { onSettingsClick(topicId) },
        onCopyMessage = { viewModel.copyMessage(it) },
        onSendText = { viewModel.sendTextMessage(it) },
        onSendImage = { viewModel.sendImageMessage(it) },
        onSendVideo = { viewModel.sendVideoMessage(it) },
        onSendAudio = { viewModel.sendAudioMessage(it) },
        onVoiceRecordingStateChange = { viewModel.setVoiceRecordingState(it) },
        modifier = Modifier
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineContent(
    uiState: TimelineUiState,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onCopyMessage: (Message) -> Unit,
    onSendText: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendVideo: (Uri) -> Unit,
    onSendAudio: (File) -> Unit,
    onVoiceRecordingStateChange: (VoiceRecordingState) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(uiState.topic?.name ?: "Loading...") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }, actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Topic Settings")
                }
            })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isOwnMessage = message.senderId == uiState.currentUserId,
                        onCopy = { onCopyMessage(message) })
                }
            }

            MessageComposer(
                onSendText = { onSendText(it) },
                onSendImage = { onSendImage(it) },
                onSendVideo = { onSendVideo(it) },
                onSendAudio = { onSendAudio(it) },
                uiState = uiState,
                onVoiceRecordingStateChange = onVoiceRecordingStateChange
            )
        }
    }
}



@Composable
fun FullScreenImage(uri: String) {
    Dialog(
        onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Full screen image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

class TimelinePreviewParameterProvider : PreviewParameterProvider<TimelineUiState> {
    override val values = sequenceOf(
        // 状态 1：加载中
        TimelineUiState(
            isLoading = true, topic = null, messages = emptyList(), currentUserId = "user_me"
        ),
        // 状态 2：正常聊天状态，有多种消息类型
        TimelineUiState(
            isLoading = false, topic = Topic(
                id = "1",
                name = "闪念 & 灵感盒",
                iconUri = "",
                creatorId = "11",
                createdAt = 1717000000000,
                updatedAt = 1717000000000
            ), currentUserId = "user_me", messages = listOf(
                Message(
                    id = "m1",
                    topicId = "1",
                    senderId = "user_other",
                    type = MessageType.TEXT,
                    content = "嗨！ImLog 感觉如何？",
                    createdAt = 1717000000000,
                ), Message(
                    id = "m2",
                    topicId = "1",
                    senderId = "user_me",
                    type = MessageType.TEXT,
                    content = "非常好用！本地优先，启动速度拉满🚀",
                    createdAt = 1717000100000
                ), Message(
                    id = "m3",
                    topicId = "1",
                    senderId = "user_other",
                    type = MessageType.IMAGE,
                    filePath = "mock_path",
                    createdAt = 1717000200000
                ), Message(
                    id = "m4",
                    topicId = "1",
                    senderId = "user_me",
                    type = MessageType.AUDIO,
                    filePath = "mock_path",
                    duration = 5,
                    createdAt = 1717000300000
                )
            )
        ),
        // 状态 3：正在录音状态
        TimelineUiState(
            isLoading = false,
            topic = Topic(
                id = "1",
                name = "闪念 & 灵感盒",
                iconUri = "",
                creatorId = "11",
                createdAt = 1717000000000,
                updatedAt = 1717000000000
            ),
            currentUserId = "user_me",
            messages = emptyList(),
            voiceRecordingState = VoiceRecordingState.RECORDING,
            voiceRecordingElapsed = 3400 // 模拟录制了 3.4 秒
        )
    )
}

@Preview(showBackground = true, name = "Timeline Multi-State Preview")
@Composable
fun TimelineScreenPreview(
    @PreviewParameter(TimelinePreviewParameterProvider::class) uiState: TimelineUiState
) {
    MaterialTheme {
        TimelineContent(
            uiState = uiState,
            onBack = {},
            onSettingsClick = {},
            onCopyMessage = {},
            onSendText = {},
            onSendImage = {},
            onSendVideo = {},
            onSendAudio = {},
            onVoiceRecordingStateChange = {})
    }
}