package top.fseasy.imlog.domain.model

import android.net.Uri
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


enum class MessageType(val value: String) {
    TEXT("text"), IMAGE("image"), VIDEO("video"), AUDIO("audio"), FILE("file"), VOICE("voice");

    companion object {
        private val valueMap = entries.associateBy(MessageType::value)
        fun fromValue(value: String) = valueMap[value]
    }
}

@JvmInline
value class MessageId(val value: String) {
    init {
        require(value.startsWith(PREFIX)) { "Invalid MessageId prefix" }
    }

    companion object {
        private const val PREFIX = "msg_"

        @OptIn(ExperimentalUuidApi::class)
        fun random(): MessageId {
            val uuid = Uuid.generateV7()
                .toHexString()
            return MessageId("${PREFIX}${uuid}")
        }
    }
}

enum class MessageFileProcessingStatus(val value: String) {
    Pending("pending"), Processing("processing"), Failed("failed");

    companion object {
        private val valueMap =
            MessageFileProcessingStatus.entries.associateBy(MessageFileProcessingStatus::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String.toMessageFileProcessingStatus(): MessageFileProcessingStatus? =
    MessageFileProcessingStatus.fromValue(this)

enum class MessageFileProcessingStage(val value: String) {
    InsertPendingMessage("insert_pending_message"), CopySrcToInternalCache(value = "copy_src2internal_cache"), GenerateThumbnail(
        "generate_thumbnail"
    ),
    CopyToSharedStorage("copy2shared_storage"), CleanWhenSuccess("clean_when_success");

    companion object {
        private val valueMap =
            MessageFileProcessingStage.entries.associateBy(MessageFileProcessingStage::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toMessageFileProcessingStage(): MessageFileProcessingStage? =
    this?.let { MessageFileProcessingStage.fromValue(it) }


enum class MessageFileProcessingErrorType(val value: String) {
    Copy2InternalFailure("copy2internal_failure"),
    SetInternalCacheToDbException(value = "set_internal_cache2db_exception"),
    UpdateProcessingStateDbNoEffect(
        value = "update_processing_db_no_effect"
    );

    companion object {
        private val valueMap =
            MessageFileProcessingErrorType.entries.associateBy(MessageFileProcessingErrorType::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.MessageFileProcessingErrorType(): MessageFileProcessingErrorType? =
    this?.let { MessageFileProcessingErrorType.fromValue(it) }


/**
 * Time/Duration all are in MS.
 * @param fileProcessStatus: null -> no file, or processing succeeded.
 */
data class Message(
    val id: MessageId,
    val topicId: TopicId,
    val senderId: UserId,
    val type: MessageType?, // null -> invalid
    val content: String? = null,
    // == media file fields
    val originalFileUri: Uri? = null,
    val fileProcessStatus: MessageFileProcessingStatus? = null,
    val originalFilename: String? = null,
    val filename: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val duration: Long? = null, // in MS
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailName: String? = null,
    // == End of media file fields
    val createdAt: Long = System.currentTimeMillis(), // in MS
    val attributesUpdatedAt: Long = createdAt,
)

object MessageFactory {
    fun createText(
        topicId: TopicId,
        senderId: UserId,
        content: String,
        timestampMs: Long,
    ): Message {
        require(content.isNotBlank()) { "Failed to create empty Text: $topicId, $senderId" }
        return Message(
            id = MessageId.random(),
            topicId = topicId,
            senderId = senderId,
            type = MessageType.TEXT,
            content = content,
            createdAt = timestampMs,
            attributesUpdatedAt = timestampMs
        )
    }

    fun createPendingMedia(
        topicId: TopicId,
        senderId: UserId,
        type: MessageType,
        timestampMs: Long,
    ): Message {
        return Message(
            id = MessageId.random(),
            topicId = topicId,
            senderId = senderId,
            type = type,
            content = null,
            createdAt = timestampMs,
            attributesUpdatedAt = timestampMs,
        )
    }
}

data class MessageFileProcessState(
    val messageId: MessageId,
    val status: MessageFileProcessingStatus,
    val processingStage: MessageFileProcessingStage,
    val errorType: MessageFileProcessingErrorType,
    val errorUserRetriable: Boolean,
    val srcUri: UriStr,
    val internalCachedFilename: String,
)

