package top.fseasy.imlog.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.ui.theme.PresetAvatar
import java.nio.file.Path
import java.nio.file.Paths


@Immutable
sealed interface AvatarUiModel {
    @Immutable
    data class Preset(@DrawableRes val resId: Int) : AvatarUiModel

    @Immutable
    data class FilePath(val path: Path) : AvatarUiModel
}

fun AvatarModel.toUIModel(): AvatarUiModel {
    return when (this) {
        is AvatarModel.FilePath -> AvatarUiModel.FilePath(path = Paths.get(this.path))
        is AvatarModel.Preset -> AvatarUiModel.Preset(PresetAvatar.fromDbNameOrRandom(this.dbName).resId)
    }
}

@Composable
fun UserAvatar(model: AvatarUiModel, modifier: Modifier = Modifier) {
    AsyncImage(
        model = when (model) {
            is AvatarUiModel.Preset -> model.resId
            is AvatarUiModel.FilePath -> model.path
        },
        contentDescription = "User Avatar",
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}