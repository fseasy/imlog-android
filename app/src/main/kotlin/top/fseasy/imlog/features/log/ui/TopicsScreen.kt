package top.fseasy.imlog.features.log.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import timber.log.Timber
import top.fseasy.imlog.domain.model.LogScreenTopic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.log.TopicsUiState
import top.fseasy.imlog.features.log.TopicsViewModel
import top.fseasy.imlog.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface TopicCardAction {
    data class Click(val topicId: TopicId) : TopicCardAction
    data class Pin(val topicId: TopicId) : TopicCardAction
    data class Archive(val topicId: TopicId) : TopicCardAction
    data class Delete(val topicId: TopicId) : TopicCardAction
    data class Settings(val topicId: TopicId) : TopicCardAction
}

sealed interface CreateTopicDialogAction {
    data object Dismiss : CreateTopicDialogAction
    data class Create(val topicName: String) : CreateTopicDialogAction
}

@Composable
fun TopicsRoute(
    onTopicClick: (TopicId) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopicsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TopicsScreenContent(
        uiState = uiState,
        onTopicCardAction = { action: TopicCardAction ->
            when (action) {
                is TopicCardAction.Click -> onTopicClick(action.topicId)
                is TopicCardAction.Pin -> viewModel.pinTopic(action.topicId)
                is TopicCardAction.Settings -> {}
                is TopicCardAction.Archive -> viewModel.archiveTopic(action.topicId)
                is TopicCardAction.Delete -> viewModel.deleteTopic(action.topicId)
            }
        },
        onCreateTopicDialogAction = { action: CreateTopicDialogAction ->
            when (action) {
                is CreateTopicDialogAction.Dismiss -> viewModel.hideCreateDialog()
                is CreateTopicDialogAction.Create -> viewModel.createTopic(action.topicName)
            }
        },
        onFloatingButtonClick = {
            Timber.i("Floating button clicked")
            Log.i("TTT", "Button click")
            viewModel.showCreateDialog()
        },
        onSettingsClick = onSettingsClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreenContent(
    uiState: TopicsUiState,
    onTopicCardAction: (TopicCardAction) -> Unit,
    onCreateTopicDialogAction: (CreateTopicDialogAction) -> Unit,
    onFloatingButtonClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onFloatingButtonClick) {
                Icon(Icons.Default.Add, contentDescription = "Create Topic")
            }
        }
    ) { paddingValues ->
        if (uiState.topics.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No topics yet. Tap + to create one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // MUST use .value as it should be saveable in bundle
                items(uiState.topics, key = { it.id.value }) { topic ->
                    TopicCard(
                        topic = topic,
                        onTopicCardAction = onTopicCardAction
                    )
                }
            }
        }

        if (uiState.showCreateDialog) {
            CreateTopicDialog(onCreateTopicDialogAction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicCard(
    topic: LogScreenTopic,
    onTopicCardAction: (TopicCardAction) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    Card(
        onClick = { onTopicCardAction(TopicCardAction.Click(topic.id)) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (topic.isPinned)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = topic.name.firstOrNull()
                            ?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = topic.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (topic.isPinned) {
                        Icon(
                            painterResource(R.drawable.icon_keep),
                            contentDescription = "Pinned",
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = dateFormat.format(Date(topic.messageUpdatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Topic Menu")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (topic.isPinned) "Unpin" else "Pin") },
                        leadingIcon = { Icon(painterResource(R.drawable.icon_keep), null) },
                        onClick = {
                            showMenu = false
                            onTopicCardAction(TopicCardAction.Pin(topic.id))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        leadingIcon = { Icon(painterResource(R.drawable.icon_archive), null) },
                        onClick = {
                            showMenu = false
                            onTopicCardAction(TopicCardAction.Archive(topic.id))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = {
                            showMenu = false
                            onTopicCardAction(TopicCardAction.Delete(topic.id))
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun CreateTopicDialog(
    onAction: (CreateTopicDialogAction) -> Unit,
) {
    var topicName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onAction(CreateTopicDialogAction.Dismiss) },
        title = { Text("Create Topic") },
        text = {
            OutlinedTextField(
                value = topicName,
                onValueChange = { topicName = it },
                label = { Text("Topic Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(CreateTopicDialogAction.Create(topicName)) },
                enabled = topicName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(CreateTopicDialogAction.Dismiss) }) {
                Text("Cancel")
            }
        }
    )
}
