package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.ui.model.AvatarUiModel
import top.fseasy.imlog.ui.model.random
import top.fseasy.imlog.ui.theme.ImlogTheme

@Composable
fun HomeRoute(
    onNavigateToTopic: (TopicId) -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToTopicSettings: (TopicId) -> Unit,
    onNavigateToCreateTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
  HomeContent(
      moreOptionMenuAction =
          MoreOptionMenuAction(
              onCreateTopic = onNavigateToCreateTopic,
              onOpenAppSettings = onNavigateToAppSettings,
          ),
      topicItemListContent = { modifier ->
        HomeTopicList(
            onClickTopic = onNavigateToTopic,
            onClickTopicSetting = onNavigateToTopicSettings,
            modifier = modifier,
        )
      },
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    moreOptionMenuAction: MoreOptionMenuAction,
    topicItemListContent: @Composable (modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
      modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        TopAppBar(
            title = { Logo() },
            actions = {
              TopBarAction(moreOptionMenuAction)
            },
            scrollBehavior = scrollBehavior,
        )
      },
  ) { paddingValues ->
    topicItemListContent(Modifier.padding(paddingValues))
  }
}

// --- Preview

@Preview(name = "Home Screen Framework", showBackground = true)
@Composable
fun HomeContentFrameworkPreview() {
  ImlogTheme {
    HomeContent(
        moreOptionMenuAction =
            MoreOptionMenuAction(
                onCreateTopic = {},
                onOpenAppSettings = {},
            ),
        topicItemListContent = { modifier ->
          Box(modifier = modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.error)) {}
        },
    )
  }
}

@Preview(name = "Home Screen With List", showBackground = true)
@Composable
fun HomeContentListPreview() {
  ImlogTheme {
    HomeContent(
        moreOptionMenuAction =
            MoreOptionMenuAction(
                onCreateTopic = {},
                onOpenAppSettings = {},
            ),
        topicItemListContent = { modifier ->
          TopicItemListContent(
              topics =
                  listOf(
                      HomeTopicUiModel(
                          id = TopicId.random(),
                          name = "Test Topic1",
                          avatarUiModel = AvatarUiModel.Preset.random(),
                          isPinned = true,
                          hasUnread = true,
                          messageFormatedUpdatedAt = "Today",
                          messageSnippet =
                              MessageSnippet(
                                  header = "[dummy]",
                                  content = "Hello",
                              ),
                      ),
                      HomeTopicUiModel(
                          id = TopicId.random(),
                          name = "Topic2",
                          avatarUiModel = AvatarUiModel.Preset.random(),
                          isPinned = false,
                          hasUnread = false,
                          messageFormatedUpdatedAt = "Yesterday",
                          messageSnippet =
                              MessageSnippet(
                                  header = "",
                                  content = "这个样式如何呢？",
                              ),
                      ),
                  ),
              onClickTopic = {},
              onTogglePin = { _, _ -> },
              onClickTopicSetting = {},
              modifier = modifier,
          )
        },
    )
  }
}
