package top.fseasy.imlog.domain.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class MessageType(val value: String) {
    TEXT("text"), IMAGE("image"), VIDEO("video"), AUDIO("audio"), FILE("file");

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

data class Message(
    val id: MessageId,
    val topicId: TopicId,
    val senderId: UserId,
    val type: MessageType?, // null -> invalid
    val content: String? = null,
    val filePath: String? = null,
    val fileSize: Long? = null,
    // All time/duration in ms
    val duration: Long? = null,
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
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

enum class MessageMediaProcessingStatus(val value: String) {
    PROCESSING("processing"), FAILED("failed");

    companion object {
        private val valueMap = MessageType.entries.associateBy(MessageType::value)
        fun fromValue(value: String) = valueMap[value]
    }
}