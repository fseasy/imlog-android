package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import top.fseasy.imlog.R

@Composable
internal fun TopBarAction(moreOptionMenuAction: MoreOptionMenuAction) {
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
          moreOptionMenuAction.onOpenAppSettings()
        },
        leadingIcon = {
          Icon(Icons.Default.Settings, contentDescription = null)
        },
    )
  }
}

@Composable
internal fun Logo() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.app_name),
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            ),
    )
  }
}
