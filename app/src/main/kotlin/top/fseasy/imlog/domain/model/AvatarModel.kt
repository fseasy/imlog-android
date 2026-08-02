package top.fseasy.imlog.domain.model

import kotlinx.serialization.Serializable
import timber.log.Timber
import top.fseasy.imlog.domain.util.defaultJson
import kotlin.enums.enumEntries

// 1. 抽象公共预设接口
interface PresetAvatar

enum class UserPresetAvatar : PresetAvatar {
  Rabbit,
  Panda,
  Fox,
}

enum class TopicPresetAvatar : PresetAvatar {
  Leaf,
  Flower,
  Raindrop,
}

@Serializable
sealed interface AvatarModel<out T : PresetAvatar> {
  @Serializable data class StorageFile(val name: String) : AvatarModel<Nothing>

  @Serializable
  data class Preset<T : PresetAvatar>(val value: T) : AvatarModel<T> {
    companion object {
      inline fun <reified T> default(): Preset<T> where T : Enum<T>, T : PresetAvatar {
        return Preset(enumEntries<T>().first())
      }

      inline fun <reified T> random(): Preset<T> where T : Enum<T>, T : PresetAvatar {
        return Preset(enumEntries<T>().random())
      }
    }
  }
}

typealias UserAvatarModel = AvatarModel<UserPresetAvatar>

typealias TopicAvatarModel = AvatarModel<TopicPresetAvatar>

fun defaultUserPresetAvatar(): UserAvatarModel = AvatarModel.Preset.default()

fun defaultTopicPresetAvatar(): TopicAvatarModel = AvatarModel.Preset.default()

fun <T : PresetAvatar> AvatarModel<T>.serialize(): String = defaultJson.encodeToString(this)

inline fun <reified T : PresetAvatar> String.toAvatarModelOrNull(): AvatarModel<T>? = runCatching {
  defaultJson.decodeFromString<AvatarModel<T>>(this)
}
    .getOrElse { e ->
      Timber.w(e, "Deserialization AvatarModel failed, s=[$this]")
      null
    }
