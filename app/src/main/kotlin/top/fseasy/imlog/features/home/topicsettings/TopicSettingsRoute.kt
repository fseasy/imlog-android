package top.fseasy.imlog.features.home.topicsettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.features.home.getArchiveListItemResource
import top.fseasy.imlog.features.home.getPinListItemResource
import top.fseasy.imlog.ui.components.AppCircularProgress
import top.fseasy.imlog.ui.components.AppInternalErrorContent
import top.fseasy.imlog.ui.model.AvatarUiModel
import top.fseasy.imlog.ui.model.random
import top.fseasy.imlog.ui.theme.ImlogTheme

@Composable
fun TopicSettingsRoute(
    onBack: () -> Unit,
    afterDeleteNavigate: () -> Unit,
    viewModel: TopicSettingsViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

  when (uiState) {
    TopicSettingsUiState.Loading -> AppCircularProgress()
    is TopicSettingsUiState.Error ->
        AppInternalErrorContent((uiState as TopicSettingsUiState.Error).userFriendlyReason)
    is TopicSettingsUiState.Success,
    ->
        TopicSettingsContent(
            uiState = uiState as TopicSettingsUiState.Success,
            onBack = onBack,
            onTogglePin = { viewModel.togglePin() },
            onToggleArchive = { viewModel.toggleArchive() },
            onDeleteTopic = {
              viewModel.deleteTopic()
              afterDeleteNavigate()
            },
            onUpdateTopicName = { newName -> viewModel.updateTopicName(newName) },
        )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicSettingsContent(
    uiState: TopicSettingsUiState.Success,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeleteTopic: () -> Unit,
    onUpdateTopicName: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  var showEditNameDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.topic_settings_title)) },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.term_back),
                )
              }
            },
        )
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
    ) {
      ListItem(
          headlineContent = { Text(stringResource(R.string.topic_settings_edit_name)) },
          supportingContent = { Text(uiState.topic.name) },
          leadingContent = { Icon(painterResource(R.drawable.icon_image), null) },
          modifier = Modifier.fillMaxWidth().clickable { showEditNameDialog = true },
      )
      HorizontalDivider()

      TopicPinSetting(
          isPinned = uiState.topic.isPinned,
          onTogglePin = onTogglePin,
          modifier = Modifier,
      )

      TopicArchiveSetting(
          isArchived = uiState.topic.isArchived,
          onToggleArchive = onToggleArchive,
          modifier = Modifier,
      )

      HorizontalDivider()

      ListItem(
          headlineContent = {
            Text(
                text = stringResource(R.string.topic_settings_delete_topic),
                color = MaterialTheme.colorScheme.error,
            )
          },
          leadingContent = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
          },
          modifier = Modifier.fillMaxWidth().clickable { showDeleteDialog = true },
      )
    }

    if (showEditNameDialog) {
      EditTopicNameDialog(
          onDismiss = { showEditNameDialog = false },
          onConfirm = { newName ->
            onUpdateTopicName(newName)
            showEditNameDialog = false
          },
      )
    }

    if (showDeleteDialog) {
      DeleteTopicDialog(
          onDismiss = { showDeleteDialog = false },
          onConfirm = {
            showDeleteDialog = false
            onDeleteTopic()
          },
      )
    }
  }
}

@Composable
private fun TopicPinSetting(
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val res =
      remember(isPinned) {
        getPinListItemResource(isPinned)
      }

  ListItem(
      headlineContent = { Text(stringResource(R.string.topic_settings_pin_topic)) },
      supportingContent = { Text(stringResource(res.supportingStringRes)) },
      leadingContent = {
        Icon(
            painterResource(res.iconRes),
            contentDescription = null,
        )
      },
      trailingContent = {
        Switch(
            checked = isPinned,
            onCheckedChange = { onTogglePin() },
        )
      },
      modifier = modifier.fillMaxWidth().clickable { onTogglePin() },
  )
}

@Composable
private fun TopicArchiveSetting(
    isArchived: Boolean,
    onToggleArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val res =
      remember(isArchived) {
        getArchiveListItemResource(isArchived)
      }

  ListItem(
      headlineContent = { Text(stringResource(R.string.topic_settings_archive_topic)) },
      supportingContent = { Text(stringResource(res.supportingStringRes)) },
      leadingContent = {
        Icon(
            painterResource(res.iconRes),
            contentDescription = null,
        )
      },
      trailingContent = {
        Switch(
            checked = isArchived,
            onCheckedChange = { onToggleArchive() },
        )
      },
      modifier = modifier.fillMaxWidth().clickable { onToggleArchive() },
  )
}

@Composable
private fun EditTopicNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
  var editedName by rememberSaveable() { mutableStateOf("") }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.topic_settings_edit_name)) },
      text = {
        OutlinedTextField(
            value = editedName,
            onValueChange = { editedName = it },
            label = { Text(stringResource(R.string.topic_settings_name_input_field_label)) },
            singleLine = true,
        )
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(editedName) }) {
          Text(stringResource(R.string.btn_save))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.btn_cancel))
        }
      },
  )
}

@Composable
fun DeleteTopicDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.topic_settings_delete_confirm_title)) },
      text = { Text(stringResource(R.string.topic_settings_delete_confirm_desc)) },
      confirmButton = {
        TextButton(onClick = onConfirm) {
          Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.btn_cancel))
        }
      },
  )
}

@Preview("Topic Setting Content", showBackground = true, showSystemUi = true)
@Composable
fun TopicSettingContentPreview() {
  val uiState =
      TopicSettingsUiState.Success(
          userId = UserId.random(),
          topic =
              SettingsTopicUiModel(
                  id = TopicId.random(),
                  name = "Dummy Topic",
                  avatarUiModel = AvatarUiModel.Preset.random(),
                  description = null,
                  isPinned = false,
                  isArchived = false,
              ),
      )
  ImlogTheme() {
    TopicSettingsContent(
        uiState = uiState,
        onBack = {},
        onTogglePin = {},
        onToggleArchive = {},
        onDeleteTopic = {},
        onUpdateTopicName = {},
    )
  }
}
