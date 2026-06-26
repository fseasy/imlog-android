package top.fseasy.imlog.domain.model

data class AudioMetadata(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val duration: Long,
)

data class VideoMetadata(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val duration: Long,
    val width: Int,
    val height: Int,
)

data class ImageMetadata(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

/**
 * Superset of all media metadata fields.
 * Used as a common transfer object between business logic and database layer.
 */
data class MediaMetadataUnion(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val duration: Long?,
)

fun AudioMetadata.toMetadataUnion() = MediaMetadataUnion(
    displayName = displayName,
    fileSize = fileSize,
    mimeType = mimeType,
    duration = duration,
    width = null,
    height = null
)

fun VideoMetadata.toMetadataUnion() = MediaMetadataUnion(
    displayName = displayName,
    fileSize = fileSize,
    mimeType = mimeType,
    width = width,
    height = height,
    duration = duration,
)

fun ImageMetadata.toMetadataUnion() = MediaMetadataUnion(
    displayName = displayName,
    fileSize = fileSize,
    mimeType = mimeType,
    width = width,
    height = height,
    duration = null,
)

enum class MessageAudioErrorStage(val value: String) {
    CopySrcToInternalCache(value = "copy_src2internal_cache"),
    SetInternalFilenameToDb(value = "set_internal_filename2db"),
    CopyToSharedStorage("copy2shared_storage"),
    SetRawFilenameToDb("set_raw_filename2db"),
    Clean("clean");

    companion object {
        private val valueMap = entries.associateBy(MessageAudioErrorStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toMessageAudioErrorStage(): MessageAudioErrorStage? =
    this?.let { MessageAudioErrorStage.fromValue(it) }
