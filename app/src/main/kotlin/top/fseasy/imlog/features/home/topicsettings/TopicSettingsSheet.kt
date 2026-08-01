package top.fseasy.imlog.features.home.topicsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.AppCircularProgress
import top.fseasy.imlog.ui.components.AppInternalErrorContent

@Composable
fun TopicSettingsSheet(
    onBack: () -> Unit,
    afterDeleteNavigate: () -> Unit,
    viewModel: TopicSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

    when (uiState) {
        TopicSettingsUiState.Loading -> AppCircularProgress()
        is TopicSettingsUiState.Error -> AppInternalErrorContent((uiState as TopicSettingsUiState.Error).userFriendlyReason)
        is TopicSettingsUiState.Success,
            -> TopicSettingsSheetContent(
            uiState = uiState as TopicSettingsUiState.Success,
            onBack = onBack,
            onTogglePin = { viewModel.togglePin() },
            onToggleArchive = { viewModel.toggleArchive() },
            onDeleteTopic = {
                viewModel.deleteTopic()
                afterDeleteNavigate()
            },
            onUpdateTopicName = { newName -> viewModel.updateTopicName(newName) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicSettingsSheetContent(
    uiState: TopicSettingsUiState.Success,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeleteTopic: () -> Unit,
    onUpdateTopicName: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onBack, sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.topic_settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.topic_settings_edit_name)) },
                supportingContent = { Text(uiState.topic.name) },
                leadingContent = { Icon(painterResource(R.drawable.icon_image), null) },
                modifier = Modifier.fillMaxWidth()
            )

            TopicPinSetting(
                isPinned = uiState.preference.isPinned,
                onTogglePin = onTogglePin,
                modifier = Modifier,
            )

            TopicArchiveSetting(
                isArchived = uiState.preference.isArchived,
                onToggleArchive = onToggleArchive,
                modifier = Modifier
            )

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.topic_settings_delete_topic),
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showEditNameDialog) {
        EditTopicNameDialog(onDismiss = { showEditNameDialog = false }, onConfirm = { newName ->
            onUpdateTopicName(newName)
            showEditNameDialog = false
        })
    }

    if (showDeleteDialog) {
        DeleteTopicDialog(onDismiss = { showDeleteDialog = false }, onConfirm = {
            showDeleteDialog = false
            onDeleteTopic()
        })
    }
}


private data class ToggleElementResource(
    val supportingStringId: Int,
    val buttonStringId: Int,
)

@Composable
private fun TopicPinSetting(
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = remember(isPinned) {
        if (isPinned) {
            ToggleElementResource(
                supportingStringId = R.string.topic_settings_pinned_status,
                buttonStringId = R.string.topic_settings_btn_unpin
            )
        } else {
            ToggleElementResource(
                supportingStringId = R.string.topic_settings_unpinned_status,
                buttonStringId = R.string.topic_settings_btn_pin
            )

        }
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.topic_settings_pin_topic)) },
        supportingContent = { Text(stringResource(res.supportingStringId)) },
        leadingContent = {
            Icon(
                painterResource(R.drawable.icon_keep), contentDescription = null
            )
        },
        modifier = modifier.fillMaxWidth(),
        trailingContent = {
            TextButton(onClick = onTogglePin) {
                Text(stringResource(res.buttonStringId))
            }
        })
}


@Composable
private fun TopicArchiveSetting(
    isArchived: Boolean,
    onToggleArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = remember(isArchived) {
        if (isArchived) {
            ToggleElementResource(
                supportingStringId = R.string.topic_settings_archived_status,
                buttonStringId = R.string.topic_settings_btn_unarchive
            )
        } else {
            ToggleElementResource(
                supportingStringId = R.string.topic_settings_unarchived_status,
                buttonStringId = R.string.topic_settings_btn_archive
            )

        }
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.topic_settings_archive_topic)) },
        supportingContent = { Text(stringResource(res.supportingStringId)) },
        leadingContent = {
            Icon(
                painterResource(R.drawable.icon_archive), contentDescription = null
            )
        },
        modifier = modifier.fillMaxWidth(),
        trailingContent = {
            TextButton(onClick = onToggleArchive) {
                Text(stringResource(res.buttonStringId))
            }
        })
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
                singleLine = true
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
        })
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
        })
}