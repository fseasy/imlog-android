package top.fseasy.imlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import top.fseasy.imlog.ui.model.UserAvatarUiModel


@Composable
fun UserAvatar(model: UserAvatarUiModel, modifier: Modifier = Modifier) {
    AsyncImage(
        model = when (model) {
            is UserAvatarUiModel.Preset -> model.resId
            is UserAvatarUiModel.FilePath -> model.path
        },
        contentDescription = "User Avatar",
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}