package top.fseasy.imlog.ui.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import top.fseasy.imlog.R
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.domain.model.UserAvatarModel
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPresetAvatar
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import java.nio.file.Path
import kotlin.io.path.name

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
    data class NioPath(val path: Path) : UserAvatarUiModel
}


fun UserAvatarModel.Preset.toUiModel(): UserAvatarUiModel.Preset = UserAvatarUiModel.Preset(
    this.value.toResourceId(), this.value
)

/**
 * Pure cpu operations
 */
fun UserAvatarModel.StorageFile.toUiModel(
    signInUserId: UserId,
    storagePathUsecase: StoragePathUseCase,
    context: Context,
): UserAvatarUiModel.NioPath {
    val path = storagePathUsecase.buildUserAvatarStoragePath(
        signInUserId = signInUserId,
        filename = this.name
    )
    return UserAvatarUiModel.NioPath(
        path.toInternalOnly()
            .toNioPath(context)
    )
}

fun UserAvatarModel.toUiModel(
    signInUserId: UserId,
    storagePathUsecase: StoragePathUseCase,
    context: Context,
): UserAvatarUiModel {
    return when (this) {
        is UserAvatarModel.StorageFile -> this.toUiModel(
            signInUserId = signInUserId,
            storagePathUsecase = storagePathUsecase,
            context = context
        )

        is UserAvatarModel.Preset -> this.toUiModel()
    }
}

fun UserAvatarUiModel.Preset.toDomain(): UserAvatarModel.Preset =
    UserAvatarModel.Preset(this.backMapValue)

/**
 * Based on the storage path rule, we just need the path name in domain part.
 */
fun UserAvatarUiModel.NioPath.toDomain(): UserAvatarModel.StorageFile =
    UserAvatarModel.StorageFile(name = path.name)

private fun UserPresetAvatar.toResourceId(): Int {
    return when (this) {
        UserPresetAvatar.Rabbit -> R.drawable.avatar_user_rabbit
        UserPresetAvatar.Panda -> R.drawable.avatar_user_panda
        UserPresetAvatar.Fox -> R.drawable.avatar_user_fox
    }
}
