package top.fseasy.imlog.features.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.ResourceModel
import top.fseasy.imlog.features.log.MessageUiState
import top.fseasy.imlog.util.secondsToMinutesSeconds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun MessageBubble(
    messageUiState: MessageUiState, isOwnMessage: Boolean, onCopy: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp), colors = CardDefaults.cardColors(
                containerColor = if (isOwnMessage) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            val message = messageUiState.message
            when (message.type) {
                MessageType.TEXT, null -> {
                    Text(
                        text = message.content ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MessageType.VOICE -> {
                    AudioPlayer(
                        resourceModel = messageUiState.thumbnailModel,
                        duration = message.duration ?: 0,
                        isOwnMessage = isOwnMessage
                    )
                }

                MessageType.IMAGE -> {
                    AsyncImage(
                        model = messageUiState.thumbnailModel,
                        contentDescription = "Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { },
                        contentScale = ContentScale.FillWidth
                    )
                }

                MessageType.VIDEO -> {
                    VideoPlayer(
                        resourceModel = messageUiState.thumbnailModel,
                        duration = message.duration ?: 0,
                        isOwnMessage = isOwnMessage
                    )
                }

                MessageType.AUDIO -> {
                    AudioPlayer(
                        resourceModel = messageUiState.thumbnailModel,
                        duration = message.duration ?: 0,
                        isOwnMessage = isOwnMessage
                    )
                }

                MessageType.FILE -> {
                    Text(
                        text = "File: ${message.originalFilename}",
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
                color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun VideoPlayer(
    resourceModel: ResourceModel?, duration: Long, isOwnMessage: Boolean,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.padding(8.dp)) {
        if (resourceModel != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black), contentAlignment = Alignment.Center
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
            text = duration.secondsToMinutesSeconds(), style = MaterialTheme.typography.labelSmall
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
                when (isPlaying) {
                    true -> Icon(painterResource(R.drawable.icon_pause), "Pause")
                    false -> Icon(Icons.Default.PlayArrow, "Play")
                }
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
    resourceModel: ResourceModel?, duration: Long, isOwnMessage: Boolean,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { isPlaying = !isPlaying }) {
                when (isPlaying) {
                    true -> Icon(painterResource(R.drawable.icon_pause), "Pause")
                    false -> Icon(Icons.Default.PlayArrow, "Play")
                }
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
                        text = (currentPosition * duration).toLong()
                            .secondsToMinutesSeconds(),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = duration.secondsToMinutesSeconds(),
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
            Icon(painterResource(R.drawable.icon_speed), null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("${playbackSpeed}x")
        }
    }
}