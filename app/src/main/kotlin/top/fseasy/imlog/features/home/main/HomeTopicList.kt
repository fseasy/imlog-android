package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.ui.components.AppCircularProgress

@Composable
internal fun HomeTopicList(
    onClickTopic: (TopicId) -> Unit,
    onClickTopicSetting: (TopicId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeTopicListViewModel = hiltViewModel(),
) {
  val topicsState by viewModel.topicListUiStateFlow.collectAsStateWithLifecycle()

  TopicItemListDispatcher(
      topicsState = topicsState,
      onClickTopic = onClickTopic,
      onTogglePin = { topicId, currentPinState -> viewModel.pinTopic(topicId, currentPinState) },
      onClickTopicSetting = onClickTopicSetting,
      modifier = modifier,
  )
}

@Composable
private fun TopicItemListDispatcher(
    topicsState: HomeTopicListUiState,
    modifier: Modifier = Modifier,
    onClickTopic: (TopicId) -> Unit,
    onTogglePin: (TopicId, Boolean) -> Unit,
    onClickTopicSetting: (TopicId) -> Unit,
) {
  if (topicsState.loading) {
    AppCircularProgress(modifier = modifier)
  } else if (topicsState.topics.isEmpty()) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
      Text(stringResource(R.string.topic_list_empty_text))
    }
  } else {
    TopicItemListContent(
        topics = topicsState.topics,
        onClickTopic = onClickTopic,
        onTogglePin = onTogglePin,
        onClickTopicSetting = onClickTopicSetting,
        modifier = modifier,
    )
  }
}

@Composable
internal fun TopicItemListContent(
    topics: List<HomeTopicUiModel>,
    onClickTopic: (TopicId) -> Unit,
    onTogglePin: (TopicId, Boolean) -> Unit,
    onClickTopicSetting: (TopicId) -> Unit,
    modifier: Modifier = Modifier,
) {
  var activeMenuTopicId by remember { mutableStateOf<TopicId?>(null) }
  val listState = rememberLazyListState()

  // When scrolling, dismiss the dropdown menu
  LaunchedEffect(listState.isScrollInProgress) {
    if (listState.isScrollInProgress) {
      activeMenuTopicId = null
    }
  }

  LazyColumn(
      // Extra 16.dp to padding the top area
      modifier = modifier.fillMaxSize().padding(top = 16.dp),
      state = listState,
  ) {
    // key MUST use .value as it should be savable in bundle
    items(topics, key = { it.id.value }) { topic ->
      HomeTopicListItem(
          topic = topic,
          isContextMenuVisible = activeMenuTopicId == topic.id,
          onDismissContextMenu = { activeMenuTopicId = null },
          onClick = {
            activeMenuTopicId = null
            onClickTopic(topic.id)
          },
          onLongClick = {
            activeMenuTopicId = topic.id
          },
          onPinClick = {
            activeMenuTopicId = null
            onTogglePin(topic.id, topic.isPinned)
          },
          onSettingClick = {
            activeMenuTopicId = null
            onClickTopicSetting(topic.id)
          },
      )
    }
  }
}
