package top.fseasy.imlog.features.home.topiclog.timeline

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.ShowFullScreenMessageUiModelAction
import top.fseasy.imlog.features.home.topiclog.timeline.contextmenu.MessageContextMenu
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.MessageBubble
import top.fseasy.imlog.ui.components.contextmenu.rememberContextMenuState

@Composable
fun MessageTimeline(
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
    pagedItems: LazyPagingItems<TimelineItemUiModel>,
    onTapEmptyArea: () -> Unit,
    onDragList: () -> Unit,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    onShowFullScreenMessage: ShowFullScreenMessageUiModelAction,
    onOpenFile: (MessageUiModel<MessageContentUiModel.GenericFile>) -> Unit,
    modifier: Modifier = Modifier,
) {
  val messageListState = rememberLazyListState()
  // Clear focus & inputMode when user drag timeline list
  val currentOnDragList by rememberUpdatedState(onDragList)
  LaunchedEffect(messageListState) {
    messageListState.interactionSource.interactions.collect { interaction ->
      if (interaction is DragInteraction.Start) {
        currentOnDragList()
      }
    }
  }

  var lastSeenMessageKey by rememberSaveable { mutableStateOf<String?>(null) }

  // Scroll to latest message if getting new and user isn't in history viewing.
  LaunchedEffect(pagedItems) {
    // Get the latest message item
    snapshotFlow {
      pagedItems.itemSnapshotList.items.firstOrNull { it is TimelineItemUiModel.MessageItem }
          as? TimelineItemUiModel.MessageItem
    }
        .filterNotNull()
        .distinctUntilChanged { old, new -> old.key == new.key }
        .collect { latestItem ->
          if (lastSeenMessageKey == null) {
            lastSeenMessageKey = latestItem.key
            return@collect
          }
          if (lastSeenMessageKey != latestItem.key) {
            lastSeenMessageKey = latestItem.key
            // Set a relative big value
            val historyMessageNumThreshold = 2
            val isViewingHistory =
                messageListState.firstVisibleItemIndex >= historyMessageNumThreshold
            val isOwnMessage = latestItem.message.sender is MessageSenderUiModel.Own
            if (isOwnMessage || !isViewingHistory) {
              // go to bottom
              val longDistanceMessageNumThreshold = 6
              if (messageListState.firstVisibleItemIndex >= longDistanceMessageNumThreshold) {
                messageListState.scrollToItem(0)
              } else {
                messageListState.animateScrollToItem(0)
              }
            }
          }
        }
  }

  val isListEmpty =
      pagedItems.loadState.refresh is LoadState.NotLoading && pagedItems.itemCount == 0

  val contextMenuState = rememberContextMenuState<AnyMessageUiModel>()

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
  }
  MessageContextMenu(
      contextMenuState,
      onShowFullScreenTextSelection = onShowFullScreenMessage.showTextSelection,
  )
}
