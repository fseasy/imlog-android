package top.fseasy.imlog.domain.model

import android.net.Uri
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class MessageType(val value: String) {
    TEXT("text"),
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    FILE("file"),
    VOICE("voice");

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

enum class MessageMediaProcessingStatus(val value: String) {
    PROCESSING("processing"), FAILED("failed");

    companion object {
        private val valueMap =
            MessageMediaProcessingStatus.entries.associateBy(MessageMediaProcessingStatus::value)

        fun fromValue(value: String) = valueMap[value]
    }
}

fun String?.toMessageMediaProcessStatus(): MessageMediaProcessingStatus? =
    this?.let { MessageMediaProcessingStatus.fromValue(it) }

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
    val fileProcessStatus: MessageMediaProcessingStatus? = null,
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

sealed interface MessageMediaCopySource {
    class FromUri(val uri: Uri) : MessageMediaCopySource
    class FromFile(val file: File) : MessageMediaCopySource
}