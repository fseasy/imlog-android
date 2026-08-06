package top.fseasy.imlog.features.home.topiclog

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.home.topiclog.composer.MessageComposer
import top.fseasy.imlog.features.home.topiclog.composer.MessageComposerViewModel
import top.fseasy.imlog.features.home.topiclog.timeline.ContextState
import top.fseasy.imlog.features.home.topiclog.timeline.MessageTimeline
import top.fseasy.imlog.features.home.topiclog.timeline.MessageTimelineViewModel

@Composable
fun TopicLogScreen(
  onNavigateBack: () -> Unit,
  onSettingsClick: (TopicId) -> Unit,
  messageTimelineViewModel: MessageTimelineViewModel = hiltViewModel(),
  composerViewModel: MessageComposerViewModel = hiltViewModel(),
) {
  val topicName =
      (messageTimelineViewModel.contextStateFlow.collectAsStateWithLifecycle().value
              as? ContextState.Success)
          ?.topic
          ?.name
  val focusManager = LocalFocusManager.current

  val handleComposerDismiss = {
    focusManager.clearFocus()
    composerViewModel.clearInputMode()
  }

  TopicLogContent(
    topicId = messageTimelineViewModel.topicId,
    topicName = topicName,
    onNavigateBack = onNavigateBack,
    onSettingsClick = onSettingsClick,
    timelineSection = {
        MessageTimeline(
          onTapOutside = handleComposerDismiss,
          onDragList = handleComposerDismiss,
          messageTimelineViewModel = messageTimelineViewModel,
        )
      },
    composerSection = {
        MessageComposer(
            onNavigateBack = onNavigateBack,
            viewModel = composerViewModel,
        )
      },
    handleComposerDismiss = handleComposerDismiss,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicLogContent(
    topicId: TopicId,
    topicName: String?,
    onNavigateBack: () -> Unit,
    onSettingsClick: (TopicId) -> Unit,
    timelineSection: @Composable () -> Unit,
    composerSection: @Composable () -> Unit,
    handleComposerDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(topicName ?: stringResource(R.string.common_ui_text_loading_dots))
            },
            navigationIcon = {
              IconButton(onClick = { onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
              }
            },
            actions = {
              IconButton(onClick = { onSettingsClick(topicId) }) {
                Icon(Icons.Default.Settings, stringResource(R.string.btn_setting))
              }
            },
        )
      },
      modifier = modifier.fillMaxSize(),
  ) { paddingValues ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                //                .padding(top = paddingValues.calculateTopPadding())
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
    ) {
      Box(
          modifier =
              modifier.weight(1f).fillMaxSize().pointerInput(Unit) {
                detectTapGestures {
                  handleComposerDismiss()
                }
              }
      ) {
        timelineSection()
      }

      composerSection()
    }
  }
}

// @Composable
// fun FullScreenImage(uri: String) {
//    Dialog(
//        onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false)
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(Color.Black)
//                .clickable { }, contentAlignment = Alignment.Center
//        ) {
//            AsyncImage(
//                model = uri,
//                contentDescription = "Full screen image",
//                modifier = Modifier.fillMaxSize(),
//                contentScale = ContentScale.Fit
//            )
//        }
//    }
// }
//
// class TimelinePreviewParameterProvider : PreviewParameterProvider<TimelineUiState> {
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
// }
//
// @Preview(showBackground = true, name = "Timeline Multi-State Preview")
// @Composable
// fun TimelineScreenPreview(
//    @PreviewParameter(TimelinePreviewParameterProvider::class) uiState: TimelineUiState,
// ) {
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
// }
