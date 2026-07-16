package top.fseasy.imlog.features.home.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import top.fseasy.imlog.domain.model.VoiceRecordingState
import top.fseasy.imlog.features.home.ContentUiState
import top.fseasy.imlog.R
import java.io.File

sealed interface ComposerAction {
    data class SendText(val content: String) : ComposerAction
    data class SendImage(val uri: Uri) : ComposerAction
    data class SendVideo(val uri: Uri) : ComposerAction
    data class SendAudio(val uri: Uri) : ComposerAction
    data class SendVoice(val file: File) : ComposerAction
    data class SetVoiceRecordingState(val state: VoiceRecordingState) : ComposerAction
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MessageComposer(
    uiState: ContentUiState,
    onAction: (ComposerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isVoiceMode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onAction(ComposerAction.SendImage(uri)) }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onAction(ComposerAction.SendVideo(uri)) }
    }

    Surface(
        modifier = modifier.fillMaxWidth(), shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            when (uiState.voiceRecordingState) {
                VoiceRecordingState.Idle -> {
                    if (isVoiceMode) {
                        VoiceInputOverlay(onStartRecording = {
                            if (audioPermissionState.status.isGranted) {
                                onAction(
                                    ComposerAction.SetVoiceRecordingState(
                                        VoiceRecordingState.Recording
                                    )
                                )
                            } else {
                                audioPermissionState.launchPermissionRequest()
                            }
                        }, onCancel = { isVoiceMode = false })
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
                                    onDismissRequest = { showAttachmentMenu = false }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showAttachmentMenu = false
                                                imagePicker.launch("image/*")
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painterResource(R.drawable.icon_image), null)
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
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painterResource(R.drawable.icon_video_file), null)
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
                                    onAction(ComposerAction.SendText(text))
                                    text = ""
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                                }
                            } else {
                                IconButton(onClick = {
                                    isVoiceMode = true
                                    onAction(
                                        ComposerAction.SetVoiceRecordingState(
                                            VoiceRecordingState.Idle
                                        )
                                    )
                                }) {
                                    Icon(painterResource(R.drawable.icon_mic), "Voice")
                                }
                            }
                        }
                    }
                }

                VoiceRecordingState.Recording -> {
                    VoiceRecordingOverlay(
                        onCancel = {
                            onAction(
                                ComposerAction.SetVoiceRecordingState(
                                    VoiceRecordingState.Idle
                                )
                            )
                        }, onStop = {
                            onAction(
                                ComposerAction.SetVoiceRecordingState(
                                    VoiceRecordingState.Stopped
                                )
                            )
                        }, elapsedTime = uiState.voiceRecordingElapsed
                    )
                }

                VoiceRecordingState.Stopped -> {
                    onAction(
                        ComposerAction.SetVoiceRecordingState(
                            VoiceRecordingState.Stopped
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceInputOverlay(
    onStartRecording: () -> Unit, onCancel: () -> Unit,
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
                        onLongPress = { onStartRecording() })
                }, contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.icon_mic),
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
    onCancel: () -> Unit, onStop: () -> Unit, elapsedTime: Long,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
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
                Icon(painterResource(R.drawable.icon_stop), "Stop", tint = Color.Red)
            }
        }
        Text(
            text = "Swipe down to cancel, swipe up to lock",
            style = MaterialTheme.typography.bodySmall
        )
    }
}