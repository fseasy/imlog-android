package top.fseasy.imlog.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.home.TimelineUiState
import top.fseasy.imlog.features.home.TimelineViewModel

sealed interface TimelineAction {
    data object Back : TimelineAction
    data class SettingClick(val topicId: TopicId) : TimelineAction
    data class CopyMessage(val message: Message) : TimelineAction
}


@Composable
fun TimelineScreen(
    topicId: TopicId,
    onBack: () -> Unit,
    onSettingsClick: (TopicId) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(topicId) {
        viewModel.loadTopic(topicId)
    }

    TimelineContent(
        uiState = uiState, onTimelineAction = { action: TimelineAction ->
            when (action) {
                is TimelineAction.Back -> onBack()
                is TimelineAction.CopyMessage -> {}
                is TimelineAction.SettingClick -> onSettingsClick(action.topicId)
            }
        }, onComposerAction = { action: ComposerAction ->
            when (action) {
                is ComposerAction.SendText -> viewModel.sendTextMessage(action.content)
                is ComposerAction.SendVoice -> viewModel.sendVoiceMessage(action.file)
                is ComposerAction.SendImage -> viewModel.sendImageMessage(action.uri)
                is ComposerAction.SendVideo -> viewModel.sendVideoMessage(action.uri)
                is ComposerAction.SendAudio -> viewModel.sendAudioMessage(action.uri)
                is ComposerAction.SetVoiceRecordingState -> viewModel.setVoiceRecorderState(
                    action.state
                )
            }
        }, modifier = Modifier
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineContent(
    uiState: TimelineUiState,
    onTimelineAction: (TimelineAction) -> Unit,
    onComposerAction: (ComposerAction) -> Unit,
    modifier: Modifier = Modifier,
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
                IconButton(onClick = { onTimelineAction(TimelineAction.Back) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }, actions = {
                IconButton(onClick = { onTimelineAction(TimelineAction.SettingClick(uiState.topic!!.id)) }) {
                    Icon(Icons.Default.Settings, "Topic Settings")
                }
            })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Key must be savable in bundle => primitive String does
                items(uiState.messages, key = { it.message.id.value }) { mState ->
                    MessageBubble(
                        messageUiState = mState,
                        isOwnMessage = mState.message.senderId == uiState.currentUserId,
                        onCopy = { onTimelineAction(TimelineAction.CopyMessage(mState.message)) })
                }
            }

            MessageComposer(
                uiState = uiState,
                onAction = onComposerAction,
                modifier = Modifier
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
                .clickable { }, contentAlignment = Alignment.Center
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
//
//class TimelinePreviewParameterProvider : PreviewParameterProvider<TimelineUiState> {
//    override val values = sequenceOf(
//        // 状态 1：加载中
//        TimelineUiState(
//            isLoading = true, topic = null, messages = emptyList(), currentUserId = "user_me"
//        ),
//        // 状态 2：正常聊天状态，有多种消息类型
//        TimelineUiState(
//            isLoading = false, topic = Topic(
//                id = TopicId("1"),
//                name = "闪念 & 灵感盒",
//                iconUri = "",
//                creatorId =
//            ), currentUserId = "user_me", messages = listOf(
//                MessageUiState(
//                    Message(
//                        id = "m1",
//                        topicId = "1",
//                        senderId = "user_other",
//                        type = MessageType.TEXT,
//                        content = "嗨！ImLog 感觉如何？",
//                        createdAt = 1717000000000,
//                    ), null
//                ), MessageUiState(
//                    Message(
//                        id = "m2",
//                        topicId = "1",
//                        senderId = "user_me",
//                        type = MessageType.TEXT,
//                        content = "非常好用！本地优先，启动速度拉满🚀",
//                        createdAt = 1717000100000
//                    ), null
//                )
//            )
//        ),
//        // 状态 3：正在录音状态
//        TimelineUiState(
//            isLoading = false,
//            topic = Topic(
//                id = "1",
//                name = "闪念 & 灵感盒",
//                iconUri = "",
//                creatorId = "11",
//            ),
//            currentUserId = "user_me",
//            messages = emptyList(),
//            voiceRecordingState = VoiceRecordingState.RECORDING,
//            voiceRecordingElapsed = 3400 // 模拟录制了 3.4 秒
//        )
//    )
//}
//
//@Preview(showBackground = true, name = "Timeline Multi-State Preview")
//@Composable
//fun TimelineScreenPreview(
//    @PreviewParameter(TimelinePreviewParameterProvider::class) uiState: TimelineUiState,
//) {
//    MaterialTheme {
//        TimelineContent(
//            uiState = uiState,
//            onBack = {},
//            onSettingsClick = {},
//            onCopyMessage = {},
//            onSendText = {},
//            onSendImage = {},
//            onSendVideo = {},
//            onSendAudio = {},
//            onVoiceRecordingStateChange = {})
//    }
//}