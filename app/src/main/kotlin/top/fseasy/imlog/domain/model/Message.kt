package top.fseasy.imlog.domain.model

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlin.String
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


enum class MessageType(val value: String) {
    Text("text"),
    Image("image"),
    Video("video"),
    Audio("audio"), Voice("voice"),
    GenericFile("generic_file"),
    ;

    companion object {
        private val valueMap = entries.associateBy(MessageType::value)
        fun fromValue(value: String) = valueMap[value]
    }
}

@JvmInline
@Serializable
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


data class MessageFileProcessState(
    val messageId: MessageId,
    val status: MessageFileProcessingStatus,
    val processingStage: MessageFileProcessingStage,
    val errorType: MessageFileProcessingErrorType,
    val errorUserRetriable: Boolean,
    val srcUri: UriStr,
    val internalCachedFilename: String,
)

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
            type = MessageType.Text,
            content = content,
            createdAt = timestampMs,
            attributesUpdatedAt = timestampMs
        )
    }
}

