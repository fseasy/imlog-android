package top.fseasy.imlog.features.home.topiclog.timeline

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.domain.model.TimelineMessage

/** TODO: add paging */
@Composable
fun MessageTimeline(
    onTapOutside: () -> Unit,
    onDragList: () -> Unit,
    modifier: Modifier = Modifier,
    timelineViewModel: TimelineViewModel = hiltViewModel(),
) {
  val messages by timelineViewModel.messagesStateFlow.collectAsStateWithLifecycle()
  TimelineContent(
      messages = messages ?: emptyList(), // TODO: show error when null.
      onTapOutside = onTapOutside,
      onDragList = onDragList,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineContent(
  messages: List<TimelineMessage>,
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
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
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
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Key must be savable in bundle => primitive String does
      items(messages, key = { it.id.value }) { message ->
        //                MessageBubble(
        //                    messageUiState = mState,
        //                    isOwnMessage = mState.message.senderId == uiState.currentUserId,
        //                    onCopy = {
        // onTimelineAction(TimelineAction.CopyMessage(mState.message)) })
        Text("${message.id} ${message.text}")
      }
    }
  }
}
