package top.fseasy.imlog.features.home.topiclog.timeline

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import timber.log.Timber
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.ShowFullScreenMessageUiModelAction
import top.fseasy.imlog.features.home.topiclog.timeline.contextmenu.MessageContextMenu
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.MessageBubble
import top.fseasy.imlog.ui.components.contextmenu.rememberContextMenuState
import top.fseasy.imlog.ui.components.overlaylayout.overlayRegisterViewport

@Composable
fun MessageTimeline(
    composerBaseHeightPx: Int, // 传入输入框无键盘时的基准像素高
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
      composerBaseHeightPx = composerBaseHeightPx, // 传入输入框无键盘时的基准像素高
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
    composerBaseHeightPx: Int, // 传入输入框无键盘时的基准像素高
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

  val density = LocalDensity.current
  val imeInsets = WindowInsets.ime
  val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)

  // 1. 如果在最底部，键盘弹起瞬间吸底
  val isAtBottom by remember {
    derivedStateOf { messageListState.firstVisibleItemIndex <= 1 }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { imeInsets.getBottom(density) }
        .collect { height ->
          if (height > 0 && isAtBottom) {
            messageListState.scrollToItem(0, 0)
          }
        }
  }

  Box(
      modifier =
          modifier
              .fillMaxSize()
              .systemBarsPadding()
              .navigationBarsPadding()
              .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                      Timber.i("TAB EMPTY AREA HERE!!! GOGOGO")
                      onTapEmptyArea()
                    },
                    // 必须声明：阻止任何未消费的长按手势回退触发 onTap
                    onLongPress = {},
                )
              }
              .overlayRegisterViewport()
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
      // =========================================================================
      // 【核心武器】：占位 Spacer
      // 在 reverseLayout = true 下，第一个声明的 item 位于屏幕的最物理底端！
      // 它的高度 = 输入框基础高度 + 键盘实时高度。
      // 它像一块隐形的砖头，把所有真实消息垫在输入框和键盘上方！
      // =========================================================================
      item(key = "BOTTOM_IME_INSET_SPACER") {
        Spacer(
            modifier =
                Modifier.fillMaxWidth().layout { measurable, constraints ->
                  val imeBottom = imeInsets.getBottom(this)
                  // NOTE:
                  // 1. the Parent BOX already contain the bottom navigation bar padding, same as
                  // the composer
                  // 2. when IME show, we only needs to add extra height of IME
                  // 3. BUT ime.getBottom() will contain the bottom-navigation bar padding again! as
                  // it located on
                  //    the top of the navigation bar.
                  //    In detail, IME raise from bottom edge(under the navigation bar),
                  //    and finally located on the top of navigation bar.
                  // => So just exclude the nav bar height, with min(0, v) to remove the impact of
                  // stage that IME bottom under the nav
                  val currentComposerExtraPadding =
                      (imeBottom - navigationBarBottom).coerceAtLeast(0)
                  val totalSpacerHeight = composerBaseHeightPx + currentComposerExtraPadding
                  val placeable =
                      measurable.measure(
                          constraints.copy(
                              minHeight = totalSpacerHeight,
                              maxHeight = totalSpacerHeight,
                          )
                      )
                  layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                  }
                }
        )
      }
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
