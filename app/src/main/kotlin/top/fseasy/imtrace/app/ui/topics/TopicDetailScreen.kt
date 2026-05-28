package top.fseasy.imtrace.app.ui.topics

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import top.fseasy.imtrace.app.domain.model.Message
import top.fseasy.imtrace.app.domain.model.MessageType
import top.fseasy.imtrace.app.domain.model.Topic
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailScreen(
    topicId: String,
    onBack: () -> Unit,
    onSettingsClick: (String) -> Unit,
    viewModel: TopicDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(topicId) {
        viewModel.loadTopic(topicId)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.topic?.name ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onSettingsClick(topicId) }) {
                        Icon(Icons.Default.Settings, "Topic Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isOwnMessage = message.senderId == uiState.currentUserId,
                        onCopy = { viewModel.copyMessage(message) }
                    )
                }
            }

            MessageComposer(
                onSendText = { viewModel.sendTextMessage(it) },
                onSendImage = { viewModel.sendImageMessage(it) },
                onSendVideo = { viewModel.sendVideoMessage(it) },
                onSendAudio = { viewModel.sendAudioMessage(it) },
                uiState = uiState,
                onVoiceRecordingStateChange = { viewModel.setVoiceRecordingState(it) }
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    onCopy: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOwnMessage)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            when (message.type) {
                MessageType.TEXT -> {
                    Text(
                        text = message.content ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = if (isOwnMessage)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MessageType.IMAGE -> {
                    AsyncImage(
                        model = message.filePath,
                        contentDescription = "Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { },
                        contentScale = ContentScale.FillWidth
                    )
                }
                MessageType.VIDEO -> {
                    VideoPlayer(
                        filePath = message.filePath,
                        duration = message.duration ?: 0,
                        isOwnMessage = isOwnMessage
                    )
                }
                MessageType.AUDIO -> {
                    AudioPlayer(
                        filePath = message.filePath,
                        duration = message.duration ?: 0,
                        isOwnMessage = isOwnMessage
                    )
                }
                MessageType.FILE -> {
                    Text(
                        text = "File: ${message.filePath}",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Text(
                text = dateFormat.format(Date(message.createdAt)),
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                color = if (isOwnMessage)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun VideoPlayer(
    filePath: String?,
    duration: Long,
    isOwnMessage: Boolean
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.padding(8.dp)) {
        if (filePath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    // Video playback UI would go here with Media3
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Playing",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    IconButton(
                        onClick = { isPlaying = true },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatDuration(duration.toInt()),
            style = MaterialTheme.typography.labelSmall
        )
        Slider(
            value = currentPosition,
            onValueChange = { currentPosition = it },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { isPlaying = !isPlaying }) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
            }
            IconButton(onClick = {
                playbackSpeed = when (playbackSpeed) {
                    1f -> 1.5f
                    1.5f -> 2f
                    else -> 1f
                }
            }) {
                Text("${playbackSpeed}x", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun AudioPlayer(
    filePath: String?,
    duration: Long,
    isOwnMessage: Boolean
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { isPlaying = !isPlaying }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pause"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = currentPosition,
                    onValueChange = { currentPosition = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((currentPosition * duration).toInt()),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = formatDuration(duration.toInt()),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        TextButton(onClick = {
            playbackSpeed = when (playbackSpeed) {
                1f -> 1.5f
                1.5f -> 2f
                else -> 1f
            }
        }) {
            Icon(Icons.Default.Speed, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("${playbackSpeed}x")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MessageComposer(
    onSendText: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendVideo: (Uri) -> Unit,
    onSendAudio: (File) -> Unit,
    uiState: TopicDetailUiState,
    onVoiceRecordingStateChange: (VoiceRecordingState) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isVoiceMode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onSendImage(it) }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onSendVideo(it) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            when (uiState.voiceRecordingState) {
                VoiceRecordingState.IDLE -> {
                    if (isVoiceMode) {
                        VoiceInputOverlay(
                            onStartRecording = {
                                if (audioPermissionState.status.isGranted) {
                                    onVoiceRecordingStateChange(VoiceRecordingState.RECORDING)
                                } else {
                                    audioPermissionState.launchPermissionRequest()
                                }
                            },
                            onCancel = { isVoiceMode = false }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                                    Icon(Icons.Default.Add, "Attach")
                                }
                                DropdownMenu(
                                    expanded = showAttachmentMenu,
                                    onDismissRequest = { showAttachmentMenu = false }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showAttachmentMenu = false
                                                imagePicker.launch("image/*")
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Image, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Image")
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showAttachmentMenu = false
                                                videoPicker.launch("video/*")
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.VideoFile, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Video")
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message...") },
                                maxLines = 4
                            )

                            if (text.isNotBlank()) {
                                IconButton(onClick = {
                                    onSendText(text)
                                    text = ""
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                                }
                            } else {
                                IconButton(onClick = {
                                    isVoiceMode = true
                                    onVoiceRecordingStateChange(VoiceRecordingState.IDLE)
                                }) {
                                    Icon(Icons.Default.Mic, "Voice")
                                }
                            }
                        }
                    }
                }
                VoiceRecordingState.RECORDING -> {
                    VoiceRecordingOverlay(
                        onCancel = { onVoiceRecordingStateChange(VoiceRecordingState.IDLE) },
                        onStop = { onVoiceRecordingStateChange(VoiceRecordingState.STOPPED) },
                        elapsedTime = uiState.voiceRecordingElapsed
                    )
                }
                VoiceRecordingState.STOPPED -> {
                    // Recording finished, would send audio here
                    onVoiceRecordingStateChange(VoiceRecordingState.IDLE)
                }
            }
        }
    }
}

@Composable
fun VoiceInputOverlay(
    onStartRecording: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, "Cancel")
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onStartRecording() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Mic,
                "Hold to record",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Text("Hold to record", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun VoiceRecordingOverlay(
    onCancel: () -> Unit,
    onStop: () -> Unit,
    elapsedTime: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Recording ${elapsedTime / 1000}s",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Red
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancel", tint = Color.Red)
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, "Stop", tint = Color.Red)
            }
        }
        Text(
            text = "Swipe down to cancel, swipe up to lock",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun FullScreenImage(uri: String) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Full screen image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

enum class VoiceRecordingState {
    IDLE, RECORDING, STOPPED
}

fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
