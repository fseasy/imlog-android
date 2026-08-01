package top.fseasy.imlog.features.home.main

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import timber.log.Timber
import top.fseasy.imlog.domain.model.TopicId

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
fun HomeScreen(
    onTopicClick: (TopicId) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopicsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

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


        if (uiState.showCreateDialog) {
            CreateTopicDialog(onCreateTopicDialogAction)
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
