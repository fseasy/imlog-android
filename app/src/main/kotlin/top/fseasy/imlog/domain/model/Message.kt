package top.fseasy.imlog.domain.model

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class MessageType(val value: String) {
  Text("text"),
  Image("image"),
  Video("video"),
  Audio("audio"),
  Voice("voice"),
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
      val uuid = Uuid.generateV7().toHexString()
      return MessageId("${PREFIX}${uuid}")
    }
  }
}

@Serializable
data class QuoteMessageThumbnailFileBuildingArgs(
    val messageCreatedAt: Long,
    val name: String,
)

@Serializable
data class QuoteMessage(
    val id: MessageId,
    val senderId: UserId,
    val senderNameSnapshot: String,
    val messageType: MessageType,
    val text: String,
    val thumbnail: QuoteMessageThumbnailFileBuildingArgs?,
)

/** Time/Duration all are in MS. */
data class Message(
    val id: MessageId,
    val topicId: TopicId,
    val senderId: UserId,
    val type: MessageType,
    val quoteMessage: QuoteMessage? = null,
    val text: String? = null,
    // == media file fields
    val originalFileUri: UriStr? = null,
    val originalFilename: String? = null,
    val storedFilename: String? = null,
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

/** For message preview */
@Serializable
data class MessagePreview(
    val type: MessageType,
    val textSnapshot: String? = null,
    val senderNameSnapshot: String? = null, // If null, = currentUser in business logic
)

@Serializable
enum class MessageInputMode(val value: String) {
  Text("text"),
  Voice("voice"),
  Attachment("Attachment"),
}

/** For composer draft */
@Serializable
data class MessageDraft(
    val inputMode: MessageInputMode? = null,
    val quoteMessage: QuoteMessage? = null,
    val text: String = "",
)

object MessageFactory {
  fun createText(
      topicId: TopicId,
      senderId: UserId,
      text: String,
      timestampMs: Long,
      quoteMessage: QuoteMessage? = null,
  ): Message {
    require(text.isNotBlank()) { "Failed to create empty Text: $topicId, $senderId" }
    return Message(
        id = MessageId.random(),
        topicId = topicId,
        senderId = senderId,
        type = MessageType.Text,
        text = text,
        createdAt = timestampMs,
        attributesUpdatedAt = timestampMs,
        quoteMessage = quoteMessage,
    )
  }
}
