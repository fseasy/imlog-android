package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicId

sealed interface CreateTopicDialogAction {
  data object Dismiss : CreateTopicDialogAction

  data class Create(val topicName: String) : CreateTopicDialogAction
}

@Immutable
data class MoreOptionMenuAction(
    val onCreateTopic: () -> Unit,
    val onOpenAppSettings: () -> Unit,
)

@Composable
fun HomeRoute(
    onNavigateToTopic: (TopicId) -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToTopicSettings: (TopicId) -> Unit,
    onNavigateToCreateTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
  TopicsScreenContent(
      onSelectTopic = onNavigateToTopic,
      onOpenTopicSettings = onNavigateToTopicSettings,
      moreOptionMenuAction =
          MoreOptionMenuAction(
              onCreateTopic = onNavigateToCreateTopic,
              onOpenAppSettings = onNavigateToAppSettings,
          ),
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreenContent(
    onSelectTopic: (TopicId) -> Unit,
    onOpenTopicSettings: (TopicId) -> Unit,
    moreOptionMenuAction: MoreOptionMenuAction,
    modifier: Modifier,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
      modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
    Column(modifier = modifier.fillMaxSize().padding(paddingValues)) {
      HomeTopicItemList(
          onClickTopic = onSelectTopic,
          onClickTopicSetting = onOpenTopicSettings,
          modifier = modifier,
      )
    }
  }
}

@Composable
private fun Logo() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.app_name),
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary,
            ),
    )
  }
}

@Composable
private fun TopBarAction(moreOptionMenuAction: MoreOptionMenuAction) {
  var showMenu by remember { mutableStateOf(false) }

  IconButton(onClick = { showMenu = true }) {
    Icon(
        painterResource(R.drawable.icon_more_vert),
        contentDescription = stringResource(R.string.home_screen_more_options_icon_desc),
    )
  }

  DropdownMenu(
      expanded = showMenu,
      onDismissRequest = { showMenu = false },
  ) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.home_screen_more_options_menu_item_create_topic)) },
        onClick = {
          showMenu = false
          moreOptionMenuAction.onCreateTopic()
        },
        leadingIcon = {
          Icon(Icons.Default.Add, contentDescription = null)
        },
    )

    DropdownMenuItem(
        text = { Text(stringResource(R.string.home_screen_more_options_menu_item_setting)) },
        onClick = {
          showMenu = false
          moreOptionMenuAction.onCreateTopic()
        },
        leadingIcon = {
          Icon(Icons.Default.Settings, contentDescription = null)
        },
    )
  }
}
