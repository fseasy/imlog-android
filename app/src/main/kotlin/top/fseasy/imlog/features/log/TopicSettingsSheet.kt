package top.fseasy.imlog.features.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicSettingsSheet(
    topicId: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    viewModel: TopicSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onBack, sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Topic Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ListItem(
                headlineContent = { Text("Edit Topic Name") },
                supportingContent = { Text(uiState.topic?.name ?: "") },
                leadingContent = { Icon(Icons.Default.Image, null) },
                modifier = Modifier.fillMaxWidth()
            )

//            ListItem(
//                headlineContent = { Text("Pin Topic") },
//                supportingContent = { Text(if (uiState.topic?.isPinned == true) "Pinned" else "Not pinned") },
//                leadingContent = { Icon(Icons.Default.PushPin, null) },
//                modifier = Modifier.fillMaxWidth(),
//                trailingContent = {
//                    TextButton(onClick = { viewModel.togglePin() }) {
//                        Text(if (uiState.topic?.isPinned == true) "Unpin" else "Pin")
//                    }
//                }
//            )

//            ListItem(
//                headlineContent = { Text("Archive Topic") },
//                supportingContent = { Text(if (uiState.topic?.isArchived == true) "Archived" else "Not archived") },
//                leadingContent = { Icon(Icons.Default.Archive, null) },
//                modifier = Modifier.fillMaxWidth(),
//                trailingContent = {
//                    TextButton(onClick = { viewModel.toggleArchive() }) {
//                        Text(if (uiState.topic?.isArchived == true) "Unarchive" else "Archive")
//                    }
//                }
//            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Personalization",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Topic", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Topic Name") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Topic Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTopicName(editedName)
                    showEditDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            })
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Topic?") },
            text = { Text("This action cannot be undone. All messages in this topic will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTopic()
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            })
    }
}
