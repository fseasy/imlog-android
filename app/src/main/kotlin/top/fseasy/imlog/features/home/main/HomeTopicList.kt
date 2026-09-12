package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.home.getPinListItemResource
import top.fseasy.imlog.ui.components.AppCircularProgress
import top.fseasy.imlog.ui.components.contextmenu.ContextMenuItem
import top.fseasy.imlog.ui.components.contextmenu.VerticalContextMenu
import top.fseasy.imlog.ui.components.contextmenu.contextMenuClickable
import top.fseasy.imlog.ui.components.contextmenu.rememberContextMenuState

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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      Row {
        AppCircularProgress(modifier = modifier)
        Text(stringResource(R.string.term_loading))
      }
    }
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
  val contextMenuState = rememberContextMenuState<HomeTopicUiModel>()
  val listState = rememberLazyListState()

  // When scrolling, dismiss the dropdown menu
  LaunchedEffect(listState.isScrollInProgress) {
    if (listState.isScrollInProgress) {
      contextMenuState.dismiss()
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
          modifier =
              Modifier.contextMenuClickable(
                  onClick = { onClickTopic(topic.id) },
                  onLongClickWithPosition = { position ->
                    contextMenuState.show(topic, position)
                  },
              ),
      )
    }
  }
  // Context Menu in Global level, it's an independent tree, don't need to be wrap with the
  // lazyColumn
  VerticalContextMenu(
      state = contextMenuState,
      items = { currentTopic ->
        val pinRes = getPinListItemResource(currentTopic.isPinned)
        listOf(
            ContextMenuItem(
                title = stringResource(pinRes.buttonStringRes),
                iconVector = ImageVector.vectorResource(pinRes.iconRes),
                isDestructive = false,
                onClick = { onTogglePin(currentTopic.id, currentTopic.isPinned) },
            ),
            ContextMenuItem(
                title = stringResource(R.string.btn_setting),
                iconVector = Icons.Default.Settings,
                isDestructive = false,
                onClick = { onClickTopicSetting(currentTopic.id) },
            ),
        )
      },
  )
}
