package top.fseasy.imlog.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import top.fseasy.imlog.ui.theme.PresetAvatar
import top.fseasy.imlog.util.defaultJson

data class Statistics(
    val totalDays: Long,
    val totalMessages: Long,
)

sealed interface ResourceModel {
    data class Uri(val uri: android.net.Uri) : ResourceModel
    data class File(val file: java.io.File) : ResourceModel
}

@Serializable
sealed interface AvatarModel {
    @Serializable
    @SerialName("file")
    data class FilePath(val path: String) : AvatarModel

    /**
     * @param dbName should be unique so we can map back
     */
    @Serializable
    @SerialName(value = "preset")
    data class Preset(val dbName: String) : AvatarModel
}

fun AvatarModel.toString(): String = defaultJson.encodeToString(this)
fun String.toAvatarModelOrDefault(): AvatarModel = try {
    defaultJson.decodeFromString<AvatarModel>(this)
} catch (e: SerializationException) {
    Timber.w(e, "Deserialization AvatarModel failed, s=[$this]")
    AvatarModel.Preset(PresetAvatar.first().dbName)
}
