package top.fseasy.imlog.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import top.fseasy.imlog.domain.util.defaultJson

@Serializable
sealed interface AvatarModel {
    @Serializable
    @SerialName("file")
    data class FilePath(val path: String) : AvatarModel

    @Serializable
    @SerialName(value = "user_preset")
    data class UserPreset(val value: UserPresetAvatar) : AvatarModel

    @Serializable
    @SerialName(value = "topic_preset")
    data class TopicPreset(val value: TopicPresetAvatar) : AvatarModel
}

/**
 * Don't name to `toString` as it'll be  shadowed by member: Any.toString!
 */
fun AvatarModel.serialize(): String = defaultJson.encodeToString(this)
fun String.toAvatarModelOrNull(): AvatarModel? = runCatching {
    defaultJson.decodeFromString<AvatarModel>(this)
}.getOrElse { e ->
    Timber.w(e, "Deserialization AvatarModel failed, s=[$this]")
    null // Provide null is a more concise way compared to pass a default value
}

fun defaultTopicPresetAvatar() = AvatarModel.TopicPreset(TopicPresetAvatar.first())
fun defaultUserPresetAvatar() = AvatarModel.UserPreset(UserPresetAvatar.first())

enum class UserPresetAvatar {
    Rabbit,
    Panda,
    Fox;

    companion object {
        fun random(): UserPresetAvatar = entries.random()
        fun first(): UserPresetAvatar = entries.first()
    }
}

enum class TopicPresetAvatar {
    Leaf,
    Flower,
    Raindrop;

    companion object {
        fun random(): TopicPresetAvatar = entries.random()
        fun first(): TopicPresetAvatar = entries.first()
    }
}