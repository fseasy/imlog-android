package top.fseasy.imlog.ui.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import top.fseasy.imlog.R
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.PresetAvatar
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPresetAvatar
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import java.nio.file.Path
import kotlin.io.path.name

@Immutable
sealed interface AvatarUiModel<out T : PresetAvatar> {
  @Immutable
  data class Preset<T : PresetAvatar>(
      @param:DrawableRes val resId: Int,
      val backMapValue: T,
  ) : AvatarUiModel<T> {
    companion object
  }

  @Immutable data class NioPath(val path: Path) : AvatarUiModel<Nothing>
}

typealias UserAvatarUiModel = AvatarUiModel<UserPresetAvatar>

typealias TopicAvatarUiModel = AvatarUiModel<TopicPresetAvatar>

inline fun <reified T> AvatarUiModel.Preset.Companion.getAll(): List<AvatarUiModel.Preset<T>>
    where T : Enum<T>, T : PresetAvatar {
  return enumValues<T>().map { AvatarUiModel.Preset(it.resId, it) }
}

@get:DrawableRes
val PresetAvatar.resId: Int
  get() =
      when (this) {
        UserPresetAvatar.Rabbit -> R.drawable.avatar_user_rabbit
        UserPresetAvatar.Panda -> R.drawable.avatar_user_rabbit
        UserPresetAvatar.Fox -> R.drawable.avatar_user_fox
        TopicPresetAvatar.Leaf -> R.drawable.avatar_topic_leaf
        TopicPresetAvatar.Flower -> R.drawable.avatar_topic_flower
        TopicPresetAvatar.Raindrop -> R.drawable.avatar_topic_raindrop
        else -> error("Unknown preset avatar: $this")
      }

fun <T : PresetAvatar> AvatarModel<T>.toUiModel(
    nioPathBuilder: (fileName: String) -> Path,
): AvatarUiModel<T> {
  return when (this) {
    is AvatarModel.Preset -> AvatarUiModel.Preset(value.resId, value)
    is AvatarModel.StorageFile -> AvatarUiModel.NioPath(nioPathBuilder(name))
  }
}

/** A helper function for nioPathBuilder, for UserAvatar */
fun buildUserAvatarNioPath(
    signInUserId: UserId,
    storagePathUseCase: StoragePathUseCase,
    context: Context,
    filename: String,
) =
    storagePathUseCase
        .buildUserAvatarStoragePath(signInUserId, filename)
        .toInternalOnly()
        .toNioPath(context)

/** A helper function for nioPathBuilder, for TopicAvatar */
fun buildTopicAvatarNioPath(
    signInUserId: UserId,
    storagePathUseCase: StoragePathUseCase,
    context: Context,
    filename: String,
) =
    storagePathUseCase
        .buildTopicAvatarStoragePath(signInUserId, filename)
        .toInternalOnly()
        .toNioPath(context)

fun <T : PresetAvatar> AvatarUiModel<T>.toDomain(): AvatarModel<T> {
  return when (this) {
    is AvatarUiModel.Preset -> AvatarModel.Preset(backMapValue)
    is AvatarUiModel.NioPath -> AvatarModel.StorageFile(name = path.name)
  }
}

/** For Coil AsyncImage rendering */
fun <T : PresetAvatar> AvatarUiModel<T>.toCoilModel(): Any =
    when (this) {
      is AvatarUiModel.Preset -> this.resId
      is AvatarUiModel.NioPath -> this.path.toFile() // Coil doesn't support nio path well?
    }
