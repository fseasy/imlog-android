package top.fseasy.imlog.domain.model

import kotlinx.serialization.Serializable
import timber.log.Timber
import top.fseasy.imlog.domain.util.defaultJson

// NOTE: Kotlin Serializable will save name instead of ordinal for enum class in JSON format
//       So no needs to set a String name for each enum.
enum class UserPresetAvatar {
    Rabbit, Panda, Fox;

    companion object {
        fun random(): UserPresetAvatar = entries.random()
        fun first(): UserPresetAvatar = entries.first()
    }
}

@Serializable
sealed interface UserAvatarModel {
    /**
     * See @StoragePathRuleUseCase
     */
    @Serializable
    data class StorageFile(val name: String) : UserAvatarModel

    @Serializable
    data class Preset(val value: UserPresetAvatar) : UserAvatarModel

}

fun defaultUserPresetAvatar() = UserAvatarModel.Preset(UserPresetAvatar.first())


// Don't name to `toString` as it'll be shadowed by member: Any.toString!
fun UserAvatarModel.serialize(): String = defaultJson.encodeToString(this)
fun String.toUserAvatarModelOrNull(): UserAvatarModel? = runCatching {
    defaultJson.decodeFromString<UserAvatarModel>(this)
}.getOrElse { e ->
    Timber.w(e, "Deserialization UserAvatarModel failed, s=[$this]")
    null // Provide null is a more concise way compared to pass a default value
}


enum class TopicPresetAvatar {
    Leaf, Flower, Raindrop;

    companion object {
        fun random(): TopicPresetAvatar = entries.random()
        fun first(): TopicPresetAvatar = entries.first()
    }
}

@Serializable
sealed interface TopicAvatarModel {
    @Serializable
    data class StorageFile(val name: String) : TopicAvatarModel

    @Serializable
    data class Preset(val value: TopicPresetAvatar) : TopicAvatarModel
}

fun defaultTopicPresetAvatar() = TopicAvatarModel.Preset(TopicPresetAvatar.first())


fun TopicAvatarModel.serialize(): String = defaultJson.encodeToString(this)
fun String.toTopicAvatarModelOrNull(): TopicAvatarModel? = runCatching {
    defaultJson.decodeFromString<TopicAvatarModel>(this)
}.getOrElse { e ->
    Timber.w(e, "Deserialization TopicAvatarModel failed, s=[$this]")
    null // Provide null is a more concise way compared to pass a default value
}
