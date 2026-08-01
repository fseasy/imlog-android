package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.AppCircularProgress

@Composable
internal fun TopicList(
    viewModel: TopicListViewModel = hiltViewModel(),
) {
    val topicsState by viewModel.topicsUiStateFlow.collectAsStateWithLifecycle()

}

@Composable
private fun TopicListContent(
    topicsState: TopicsUiState,
    modifier: Modifier = Modifier
) {
    if (topicsState.loading) {
        AppCircularProgress()
    } else if (topicsState.topics.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.topic_list_empty_text))
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // MUST use .value as it should be savable in bundle
            items(topicsState.topics, key = { it.id.value }) { topic ->
                TopicCard(
                    topic = topic,
                    onTopicCardAction = onTopicCardAction
                )
            }
        }
    }
}