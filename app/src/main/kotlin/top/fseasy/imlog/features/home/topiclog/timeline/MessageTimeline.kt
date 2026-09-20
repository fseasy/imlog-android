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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.ShowFullScreenMessageUiModelAction
import top.fseasy.imlog.features.home.topiclog.timeline.contextmenu.MessageContextMenu
import top.fseasy.imlog.features.home.topiclog.timeline.contextmenu.MessageTextSelectionBottomSheet
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.MessageBubble
import top.fseasy.imlog.ui.components.contextmenu.rememberContextMenuState

@Composable
fun MessageTimeline(
    messageListState: LazyListState,
    onTapEmptyArea: () -> Unit,
    onDragList: () -> Unit,
    onShowFullScreenMessage: ShowFullScreenMessageUiModelAction,
    onOpenFile: (MessageUiModel<MessageContentUiModel.GenericFile>) -> Unit,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    modifier: Modifier = Modifier,
    viewModel: MessageTimelineViewModel = hiltViewModel(),
) {
  val lazyPagingMessages = viewModel.pagedMessagesStateFlow.collectAsLazyPagingItems()

  TimelineContent(
      messageListState = messageListState,
      pagedItems = lazyPagingMessages,
      onTapEmptyArea = onTapEmptyArea,
      onDragList = onDragList,
      mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
      onShowFullScreenMessage = onShowFullScreenMessage,
      onOpenFile = onOpenFile,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineContent(
    messageListState: LazyListState,
    pagedItems: LazyPagingItems<TimelineItemUiModel>,
    onTapEmptyArea: () -> Unit,
    onDragList: () -> Unit,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    onShowFullScreenMessage: ShowFullScreenMessageUiModelAction,
    onOpenFile: (MessageUiModel<MessageContentUiModel.GenericFile>) -> Unit,
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
  val latestMessageItem =
      pagedItems.itemSnapshotList.items.firstOrNull { it is TimelineItemUiModel.MessageItem }
          as? TimelineItemUiModel.MessageItem
  LaunchedEffect(latestMessageItem) {
    if (latestMessageItem == null) return@LaunchedEffect
    val isViewingHistory = messageListState.firstVisibleItemIndex > 1
    if (!isViewingHistory) messageListState.animateScrollToItem(0)
  }

  val isListEmpty =
      pagedItems.loadState.refresh is LoadState.NotLoading && pagedItems.itemCount == 0

  val contextMenuState = rememberContextMenuState<AnyMessageUiModel>()
  var textSelectionBottomSheetPayload by rememberSaveable { mutableStateOf<String?>(null) }

  Box(
      modifier =
          modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures {
              onTapEmptyArea()
            }
          }
  ) {
    // Items
    LazyColumn(
        modifier = Modifier.fillMaxSize(), // use an empty Modifier
        state = messageListState,
        // items are ordered in time DESC, reverse will make latest message show in bottom
        reverseLayout = true,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Key must be savable in bundle => primitive String does
      items(pagedItems.itemCount, key = pagedItems.itemKey { it.key }) { index ->
        when (val item = pagedItems[index]) {
          is TimelineItemUiModel.DateSeparator -> DateDivider(text = item.formatedText)
          is TimelineItemUiModel.MessageItem ->
              MessageBubble(
                  message = item.message,
                  mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
                  onShowFullScreenMessage = onShowFullScreenMessage,
                  onOpenFile = onOpenFile,
                  onShowContextMenu = { position ->
                    contextMenuState.show(item.message, position)
                  },
              )
          null -> Unit
        }
      }
    }
    // Empty State
    if (isListEmpty) {
      EmptyTimelinePlaceholder(modifier = Modifier.align(Alignment.Center))
    }

    // Text-Selection Bottom Sheet (for Context Menu action)
    // -- Assign to `val` to enable smart cast
    val currentTextSelectionBottomSheetPayload = textSelectionBottomSheetPayload
    if (!currentTextSelectionBottomSheetPayload.isNullOrEmpty()) {
      MessageTextSelectionBottomSheet(
          text = currentTextSelectionBottomSheetPayload,
          onDismiss = { textSelectionBottomSheetPayload = null },
      )
    }
  }
  MessageContextMenu(
      contextMenuState,
      onShowTextSelection = { textMessage ->
        val textLength = textMessage.content.text.length
        if (textLength > 1) {
          onShowFullScreenMessage.showTextSelection(textMessage)
        } else {
          textSelectionBottomSheetPayload = textMessage.content.text
        }
      },
  )
}
