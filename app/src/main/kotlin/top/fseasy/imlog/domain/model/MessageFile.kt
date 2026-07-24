package top.fseasy.imlog.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class AudioMetadata(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val duration: Duration,
)

@Serializable
data class VideoMetadata(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val duration: Duration,
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

@Serializable
data class GenericFileMetadata(
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
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
    val duration: Duration?,
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

fun GenericFileMetadata.toMetadataUnion() = FileMetadataUnion(
    displayName = displayName,
    fileSize = fileSize,
    mimeType = mimeType,
    width = null,
    height = null,
    duration = null,
)

/**
 * To transfer info between coroutine and worker.
 */
@Serializable
data class FinishSendingFileWorkerPayload(
    // -- Message Info
    val messageId: MessageId,
    val userId: UserId,
    val topicId: TopicId,
    val messageTimestampMs: Long,
    val messageType: MessageType,
    // -- File Info
    val srcUriStr: UriStr?,
    val cacheFilename: String,
    val fileMetadata: FileMetadataUnion,
)

sealed interface MessageProcessingErrorStage {
    val value: String
}

// =============
// DEFINE Error Stage mapper for each message type
// - Why almost duplicated?
//   - To make the whole stages more clear for each type.
// ==============

enum class AudioMessageProcessingErrorStage(override val value: String) :
    MessageProcessingErrorStage {
    CopySrcToInternalCache(value = "copy_src2internal_cache"), SetInternalFilenameToDb(value = "set_internal_filename2db"), CopyToSharedStorage(
        "copy2shared_storage"
    ),
    SetRawFilenameToDb("set_raw_filename2db"), DeleteInternalFileCache("delete_internal_file_cache"), DeleteTaskStateFromDb(
        "delete_task_state_from_db"
    ),
    IllegalState("illegal_state") // e.g: update message/task_state table with 0 row affected
    ;

    companion object {
        private val valueMap = entries.associateBy(AudioMessageProcessingErrorStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toAudioMessageProcessingErrorStage(): AudioMessageProcessingErrorStage? =
    this?.let { AudioMessageProcessingErrorStage.fromValue(it) }

enum class VoiceMessageProcessingErrorStage(override val value: String) :
    MessageProcessingErrorStage {
    CopyToSharedStorage("copy2shared_storage"), SetRawFilenameToDb("set_raw_filename2db"), DeleteInternalFileCache(
        "delete_internal_file_cache"
    ),
    DeleteTaskStateFromDb("delete_task_state_from_db"), IllegalState("illegal_state") // e.g: update message/task_state table with 0 row affected
    ;

    companion object {
        private val valueMap = entries.associateBy(VoiceMessageProcessingErrorStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toVoiceMessageProcessingErrorStage(): VoiceMessageProcessingErrorStage? =
    this?.let { VoiceMessageProcessingErrorStage.fromValue(it) }


enum class ImageMessageProcessingErrorStage(override val value: String) :
    MessageProcessingErrorStage {
    CopySrcToInternalCache(value = "copy_src2internal_cache"), SetInternalFilenameToDb(value = "set_internal_filename2db"), CopyToSharedStorage(
        "copy2shared_storage"
    ),
    SetRawFilenameToDb("set_raw_filename2db"), GenerateThumbnail("generate_thumbnail"), SaveThumbnailFile(
        "save_thumbnail_file"
    ),
    SetThumbnailFilenameToDb("set_thumbnail_filename2db"), DeleteInternalFileCache("delete_internal_file_cache"), DeleteTaskStateFromDb(
        "delete_task_state_from_db"
    ),
    IllegalState("illegal_state") // e.g: update message/task_state table with 0 row affected
    ;

    companion object {
        private val valueMap = entries.associateBy(ImageMessageProcessingErrorStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toImageMessageProcessingErrorStage(): ImageMessageProcessingErrorStage? =
    this?.let { ImageMessageProcessingErrorStage.fromValue(it) }

enum class VideoMessageProcessingErrorStage(override val value: String) :
    MessageProcessingErrorStage {
    CopySrcToInternalCache(value = "copy_src2internal_cache"), SetInternalFilenameToDb(value = "set_internal_filename2db"), CopyToSharedStorage(
        "copy2shared_storage"
    ),
    SetRawFilenameToDb("set_raw_filename2db"), GenerateThumbnail("generate_thumbnail"), SaveThumbnailFile(
        "save_thumbnail_file"
    ),
    SetThumbnailFilenameToDb("set_thumbnail_filename2db"), DeleteInternalFileCache("delete_internal_file_cache"), DeleteTaskStateFromDb(
        "delete_task_state_from_db"
    ),
    IllegalState("illegal_state") // e.g: update message/task_state table with 0 row affected
    ;

    companion object {
        private val valueMap = entries.associateBy(VideoMessageProcessingErrorStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toVideoMessageProcessingErrorStage(): VideoMessageProcessingErrorStage? =
    this?.let { VideoMessageProcessingErrorStage.fromValue(it) }


enum class GenericFileMessageProcessingErrorStage(override val value: String) :
    MessageProcessingErrorStage {
    CopySrcToInternalCache(value = "copy_src2internal_cache"), SetInternalFilenameToDb(value = "set_internal_filename2db"), CopyToSharedStorage(
        "copy2shared_storage"
    ),
    SetRawFilenameToDb("set_raw_filename2db"), GenerateThumbnail("generate_thumbnail"), SaveThumbnailFile(
        "save_thumbnail_file"
    ),
    SetThumbnailFilenameToDb("set_thumbnail_filename2db"), DeleteInternalFileCache("delete_internal_file_cache"), DeleteTaskStateFromDb(
        "delete_task_state_from_db"
    ),
    IllegalState("illegal_state") // e.g: update message/task_state table with 0 row affected
    ;

    companion object {
        private val valueMap = entries.associateBy(GenericFileMessageProcessingErrorStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toGenericFileMessageProcessingErrorStage(): GenericFileMessageProcessingErrorStage? =
    this?.let { GenericFileMessageProcessingErrorStage.fromValue(it) }
