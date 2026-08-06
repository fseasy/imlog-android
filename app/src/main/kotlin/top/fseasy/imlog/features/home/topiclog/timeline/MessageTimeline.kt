package top.fseasy.imlog.features.home.topiclog.timeline

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.MessageBubble

@Composable
fun MessageTimeline(
    onTapOutside: () -> Unit,
    onDragList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessageTimelineViewModel = hiltViewModel(),
) {
  val lazyPagingMessages = viewModel.pagedMessagesStateFlow.collectAsLazyPagingItems()
  TimelineContent(
      pagedMessages = lazyPagingMessages,
      onTapOutside = onTapOutside,
      onDragList = onDragList,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineContent(
    pagedMessages: LazyPagingItems<MessageUiModel>,
    modifier: Modifier = Modifier,
    onTapOutside: () -> Unit,
    onDragList: () -> Unit,
) {
  val listState = rememberLazyListState()

  // Clear focus & inputMode when user drag timeline list
  val isDragged by listState.interactionSource.collectIsDraggedAsState()
  LaunchedEffect(isDragged) {
    if (isDragged) {
      onDragList()
    }
  }



  Box(
      modifier =
          modifier
              .fillMaxSize()
              .pointerInput(Unit) {
                detectTapGestures {
                  onTapOutside()
                }
              }
  ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(), // use an empty Modifier
        state = listState,
        reverseLayout =
            true, // items are ordered in time DESC, reverse will make latest message show in bottom
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Key must be savable in bundle => primitive String does
      items(pagedMessages.itemCount, key = pagedMessages.itemKey { it.id }) { index ->
        val message = pagedMessages[index]
        if (message != null) {
          MessageBubble(
            messageUiState = message,
        }
      }
    }
  }
}
