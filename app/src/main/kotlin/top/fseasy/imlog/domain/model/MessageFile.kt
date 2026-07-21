package top.fseasy.imlog.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioMetadata(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val duration: Long,
)

@Serializable
data class VideoMetadata(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val duration: Long,
    val width: Int,
    val height: Int,
)

@Serializable
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
@Serializable
data class FileMetadataUnion(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val duration: Long?,
)

fun AudioMetadata.toMetadataUnion() = FileMetadataUnion(
    displayName = displayName,
    fileSize = fileSize,
    mimeType = mimeType,
    duration = duration,
    width = null,
    height = null
)

fun VideoMetadata.toMetadataUnion() = FileMetadataUnion(
    displayName = displayName,
    fileSize = fileSize,
    mimeType = mimeType,
    width = width,
    height = height,
    duration = duration,
)

fun ImageMetadata.toMetadataUnion() = FileMetadataUnion(
    displayName = displayName,
    fileSize = fileSize,
    mimeType = mimeType,
    width = width,
    height = height,
    duration = null,
)

@Serializable
data class FinishFileSendingWorkerPayload(
    val messageId: MessageId,
    val userId: UserId,
    val topicId: TopicId,
    val messageTimestampMs: Long,
    val fileMetadata: FileMetadataUnion,
    val cacheFilename: String,
)

sealed interface MessageProcessingErrorStage {
    val value: String
}

enum class MessageAudioProcessingErrorStage(override val value: String) :
    MessageProcessingErrorStage {
    CopySrcToInternalCache(value = "copy_src2internal_cache"),
    SetInternalFilenameToDb(value = "set_internal_filename2db"),
    CopyToSharedStorage("copy2shared_storage"),
    SetRawFilenameToDb("set_raw_filename2db"),
    DeleteInternalFileCache("delete_internal_file_cache"),
    DeleteTaskStateFromDb("delete_task_state_from_db"),
    IllegalState("illegal_state") // e.g: update message/task_state table with 0 row affected
    ;

    companion object {
        private val valueMap = entries.associateBy(MessageAudioProcessingErrorStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toMessageAudioErrorStage(): MessageAudioProcessingErrorStage? =
    this?.let { MessageAudioProcessingErrorStage.fromValue(it) }


/***
 * Why almost duplicated?
 * - To make the whole stages more clear for each type.
 */
enum class MessageImageProcessingErrorStage(override val value: String) :
    MessageProcessingErrorStage {
    CopySrcToInternalCache(value = "copy_src2internal_cache"),
    SetInternalFilenameToDb(value = "set_internal_filename2db"),
    CopyToSharedStorage("copy2shared_storage"),
    SetRawFilenameToDb("set_raw_filename2db"),
    GenerateThumbnail("generate_thumbnail"),
    SaveThumbnailFile("save_thumbnail_file"),
    SetThumbnailFilenameToDb("set_thumbnail_filename2db"),
    DeleteInternalFileCache("delete_internal_file_cache"),
    DeleteTaskStateFromDb("delete_task_state_from_db"),
    IllegalState("illegal_state") // e.g: update message/task_state table with 0 row affected
    ;

    companion object {
        private val valueMap = entries.associateBy(MessageImageProcessingErrorStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toMessageImageErrorStage(): MessageImageProcessingErrorStage? =
    this?.let { MessageImageProcessingErrorStage.fromValue(it) }
