package top.fseasy.imlog.ui.model

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.data.util.NioPathParceler
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.PresetAvatar
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPresetAvatar
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.util.defaultJson
import java.nio.file.Path
import kotlin.io.path.name

@Parcelize
@Immutable
sealed interface AvatarUiModel<out T : PresetAvatar> : Parcelable {
  @Parcelize
  @Immutable
  data class Preset<T : PresetAvatar>(
      @param:DrawableRes val resId: Int,
      val backMapValue: @WriteWith<PresetAvatarParceler> T,
  ) : AvatarUiModel<T> {
    companion object
  }

  @Parcelize
  @Immutable
  data class NioPath(val path: @WriteWith<NioPathParceler> Path) : AvatarUiModel<Nothing>
}

typealias UserAvatarUiModel = AvatarUiModel<UserPresetAvatar>

typealias TopicAvatarUiModel = AvatarUiModel<TopicPresetAvatar>

inline fun <reified T> AvatarUiModel.Preset.Companion.getAll(): List<AvatarUiModel.Preset<T>>
    where T : Enum<T>, T : PresetAvatar {
  return enumValues<T>().map { AvatarUiModel.Preset(it.resId, it) }
}

inline fun <reified T> AvatarUiModel.Preset.Companion.random(): AvatarUiModel.Preset<T>
    where T : Enum<T>, T : PresetAvatar {
  return enumValues<T>().random().let { AvatarUiModel.Preset(it.resId, it) }
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
    nioPathBuilder: (filename: String) -> Path,
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

inline fun <reified T : PresetAvatar> String.toAvatarModelOrNull(): AvatarModel<T>? = runCatching {
  defaultJson.decodeFromString<AvatarModel<T>>(this)
}
    .getOrElse { e ->
      Timber.w(e, "Deserialization AvatarModel failed, s=[$this]")
      null
    }

/** For Savable (rememberSavable) in Composable */
object PresetAvatarParceler : Parceler<PresetAvatar> {

  private const val TYPE_USER = 1
  private const val TYPE_TOPIC = 2

  override fun create(parcel: Parcel): PresetAvatar {
    val presetType = parcel.readInt()
    val name = parcel.readString().orEmpty()

    return when (presetType) {
      TYPE_USER -> {
        UserPresetAvatar.entries.find { it.name == name } ?: UserPresetAvatar.entries.first()
      }
      TYPE_TOPIC -> {
        TopicPresetAvatar.entries.find { it.name == name } ?: TopicPresetAvatar.entries.first()
      }
      else -> error("Unknown parcel type: $presetType")
    }
  }

  override fun PresetAvatar.write(parcel: Parcel, flags: Int) {
    when (this) {
      is UserPresetAvatar -> {
        parcel.writeInt(TYPE_USER)
        parcel.writeString(this.name)
      }
      is TopicPresetAvatar -> {
        parcel.writeInt(TYPE_TOPIC)
        parcel.writeString(this.name)
      }
    }
  }
}
