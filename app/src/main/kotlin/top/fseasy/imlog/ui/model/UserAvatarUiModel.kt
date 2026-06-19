package top.fseasy.imlog.ui.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.UserPresetAvatar
import java.nio.file.Path
import java.nio.file.Paths

@Immutable
sealed interface UserAvatarUiModel {
    @Immutable
    data class Preset(@param:DrawableRes val resId: Int, val backMapValue: UserPresetAvatar) :
        UserAvatarUiModel {
        companion object {
            fun getAll(): List<Preset> = UserPresetAvatar.entries.map {
                Preset(it.toResourceId(), it)
            }
        }
    }

    @Immutable
    data class FilePath(val absolutePath: Path) : UserAvatarUiModel
}


fun AvatarModel.toUserAvatarUIModel(): UserAvatarUiModel {
    return when (this) {
        is AvatarModel.FilePath -> UserAvatarUiModel.FilePath(Paths.get(this.path))
        is AvatarModel.UserPreset -> UserAvatarUiModel.Preset(
            this.value.toResourceId(), this.value
        )

        else -> {
            val error = "Can't transform $this to UserAvatarUiModel"
            Timber.e(error)
            throw IllegalArgumentException(error)
        }
    }
}

fun UserAvatarUiModel.Preset.toAvatarModel(): AvatarModel {
    return AvatarModel.UserPreset(this.backMapValue)
}

private fun UserPresetAvatar.toResourceId(): Int {
    return when (this) {
        UserPresetAvatar.Rabbit -> R.drawable.avatar_rabbit
        UserPresetAvatar.Panda -> R.drawable.avatar_rabbit
        UserPresetAvatar.Fox -> R.drawable.avatar_fox
    }
}
