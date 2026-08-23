package top.fseasy.imlog.features.home.topiclog.timeline

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.MessageBubble

@Composable
fun MessageTimeline(
    messageListState: LazyListState,
    onTapOutside: () -> Unit,
    onDragList: () -> Unit,
    onFullScreenViewMessage: (MessageUiModel) -> Unit,
    onOpenFile: (MessageUiModel) -> Unit,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    modifier: Modifier = Modifier,
    viewModel: MessageTimelineViewModel = hiltViewModel(),
) {
  val lazyPagingMessages = viewModel.pagedMessagesStateFlow.collectAsLazyPagingItems()

  TimelineContent(
      messageListState = messageListState,
      pagedMessages = lazyPagingMessages,
      onTapOutside = onTapOutside,
      onDragList = onDragList,
      mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
      onShowImage = onFullScreenViewMessage,
      onShowVideo = onFullScreenViewMessage,
      onOpenFile = onOpenFile,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineContent(
    messageListState: LazyListState,
    pagedMessages: LazyPagingItems<MessageUiModel>,
    onTapOutside: () -> Unit,
    onDragList: () -> Unit,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    onShowImage: (MessageUiModel) -> Unit,
    onShowVideo: (MessageUiModel) -> Unit,
    onOpenFile: (MessageUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {

  // Clear focus & inputMode when user drag timeline list
  val isDragged by messageListState.interactionSource.collectIsDraggedAsState()
  LaunchedEffect(isDragged) {
    if (isDragged) {
      onDragList()
    }
  }

  // Scroll to latest message if getting new and user isn't in history viewing.
  val latestMessageId =
      if (pagedMessages.itemCount > 0) {
        pagedMessages.peek(0)?.id?.value
      } else null
  LaunchedEffect(latestMessageId) {
    if (latestMessageId == null) return@LaunchedEffect
    val isViewingHistory = messageListState.firstVisibleItemIndex > 1
    if (!isViewingHistory) messageListState.animateScrollToItem(0)
  }

  Box(
      modifier =
          modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures {
              onTapOutside()
            }
          }
  ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(), // use an empty Modifier
        state = messageListState,
        reverseLayout =
            true, // items are ordered in time DESC, reverse will make latest message show in bottom
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Key must be savable in bundle => primitive String does
      items(pagedMessages.itemCount, key = pagedMessages.itemKey { it.id.value }) { index ->
        val message = pagedMessages[index]
        if (message != null) {
          MessageBubble(
              message = message,
              mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
              onShowImage = onShowImage,
              onShowVideo = onShowVideo,
              onOpenFile = onOpenFile,
          )
        }
      }
    }
  }
}
