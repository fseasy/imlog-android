package top.fseasy.imlog.ui.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import top.fseasy.imlog.R
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.domain.model.TopicAvatarModel
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import java.nio.file.Path
import kotlin.io.path.name

@Immutable
sealed interface TopicAvatarUiModel {
    @Immutable
    data class Preset(@param:DrawableRes val resId: Int, val backMapValue: TopicPresetAvatar) :
        TopicAvatarUiModel {
        companion object {
            fun getAll(): List<Preset> = TopicPresetAvatar.entries.map {
                Preset(it.toResourceId(), it)
            }
        }
    }

    @Immutable
    data class NioPath(val path: Path) : TopicAvatarUiModel
}


fun TopicAvatarModel.Preset.toUiModel(): TopicAvatarUiModel.Preset = TopicAvatarUiModel.Preset(
    this.value.toResourceId(), this.value
)

/**
 * Pure cpu operations
 */
fun TopicAvatarModel.StorageFile.toUiModel(
    signInUserId: UserId,
    storagePathUsecase: StoragePathUseCase,
    context: Context,
): TopicAvatarUiModel.NioPath {
    val path = storagePathUsecase.buildTopicAvatarStoragePath(
        signInUserId = signInUserId,
        filename = this.name
    )
    return TopicAvatarUiModel.NioPath(
        path.toInternalOnly()
            .toNioPath(context)
    )
}

fun TopicAvatarModel.toUiModel(
    signInUserId: UserId,
    storagePathUsecase: StoragePathUseCase,
    context: Context,
): TopicAvatarUiModel {
    return when (this) {
        is TopicAvatarModel.StorageFile -> this.toUiModel(
            signInUserId = signInUserId,
            storagePathUsecase = storagePathUsecase,
            context = context
        )

        is TopicAvatarModel.Preset -> this.toUiModel()
    }
}

fun TopicAvatarUiModel.Preset.toDomain(): TopicAvatarModel.Preset =
    TopicAvatarModel.Preset(this.backMapValue)

/**
 * Based on the storage path rule, we just need the path name in domain part.
 */
fun TopicAvatarUiModel.NioPath.toDomain(): TopicAvatarModel.StorageFile =
    TopicAvatarModel.StorageFile(name = path.name)

private fun TopicPresetAvatar.toResourceId(): Int {
    return when (this) {
        TopicPresetAvatar.Leaf -> R.drawable.avatar_topic_leaf
        TopicPresetAvatar.Flower -> R.drawable.avatar_topic_flower
        TopicPresetAvatar.Raindrop -> R.drawable.avatar_topic_raindrop
    }
}