package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.TopicId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicCard(
    topic: HomeTopic,
    onClick: (TopicId) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    Card(
        onClick = { onClick(topic.id) },
        modifier = modifier.fillMaxWidth(),
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
private fun TopicAvatar(topic: HomeTopic) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = topic.name.firstOrNull()
                ?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
    topic.avatarModel
}