package top.fseasy.imlog.domain.model

import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import timber.log.Timber
import top.fseasy.imlog.domain.recorder.WaveformCodec5Bit
import top.fseasy.imlog.domain.recorder.deserializeToByteArray

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
    val defaultType = MessageType.Text

    fun fromValueOrDefault(value: String) =
        valueMap[value]
            ?: run {
              Timber.w("Invalid message type: $value, fallback to default value = $defaultType")
              defaultType
            }
  }
}

@JvmInline
@Serializable
value class MessageId(val value: String) {
  init {
    require(value.startsWith(PREFIX)) { "Invalid MessageId prefix" }
  }

  // NOTE!
  // UUID V7 is time-order awareness, so you can use it to order, or as a SEQUENCE signal
  companion object {
    private const val PREFIX = "msg_"

    @OptIn(ExperimentalUuidApi::class)
    fun random(): MessageId {
      val uuid = Uuid.generateV7().toHexString()
      return MessageId("${PREFIX}${uuid}")
    }
  }
}

data class QuotedMessageSender(val id: UserId?, val name: String)

data class MessageSender(
    val id: UserId,
    val name: String,
    val avatarModel: UserAvatarModel,
)

sealed interface QuotedMessageContent {
  sealed interface ImageLike : QuotedMessageContent {
    // If thumbnail hasn't generated done, it's null. We don't use fileUri as fallback here,
    // as it's an edge case and has minimal impact
    val thumbnailFilename: String?
    // used to build the thumbnail absolute path
    val createdAt: Instant
  }

  data class Text(val text: String) : QuotedMessageContent

  // audio, generic file
  data class File(val displayFilename: String, val mimeType: String) : QuotedMessageContent

  data class Voice(val duration: Duration) : QuotedMessageContent

  data class Image(override val thumbnailFilename: String?, override val createdAt: Instant) :
      ImageLike

  data class Video(override val thumbnailFilename: String?, override val createdAt: Instant) :
      ImageLike
}

sealed interface QuotedMessage {
  data object Deleted : QuotedMessage

  data class Matched(
      val id: MessageId,
      val sender: QuotedMessageSender,
      val content: QuotedMessageContent,
  ) : QuotedMessage
}

sealed interface CacheAttachmentSource {

  data class Cache(val filename: String) : CacheAttachmentSource

  data class StorageUri(val filename: String) : CacheAttachmentSource

  /** shouldn't exist but in case */
  data object IllegalState : CacheAttachmentSource
}

sealed interface UriAttachmentSource {
  data class SourceTemporary(val uriStr: UriStr) : UriAttachmentSource

  data class Storage(val filename: String) : UriAttachmentSource

  /** We don't want to raise error, just set it to illegal */
  data object IllegalState : UriAttachmentSource
}

sealed interface MessageContent {
  sealed interface UriAttachment : MessageContent {
    val displayFilename: String

    /** Could be either source-temporary-uri, or copied-storage-uri. or Illegal */
    val fileUri: UriAttachmentSource
  }

  sealed interface ImageLike : UriAttachment {
    val thumbnailFilename: String?
    val width: Int
    val height: Int
  }

  data class Text(val text: String) : MessageContent

  data class Image(
      override val displayFilename: String,
      override val fileUri: UriAttachmentSource,
      override val thumbnailFilename: String?,
      override val width: Int,
      override val height: Int,
  ) : ImageLike

  data class Video(
      override val displayFilename: String,
      override val fileUri: UriAttachmentSource,
      override val thumbnailFilename: String?,
      override val width: Int,
      override val height: Int,
      val duration: Duration,
  ) : ImageLike

  data class Audio(
      override val displayFilename: String,
      override val fileUri: UriAttachmentSource,
      val duration: Duration,
      val mimeType: String,
  ) : UriAttachment

  data class GenericFile(
      override val displayFilename: String,
      override val fileUri: UriAttachmentSource,
      val mimeType: String,
      val fileByteSize: Long,
  ) : UriAttachment

  data class Voice(
      val displayFilename: String,
      val file: CacheAttachmentSource,
      val duration: Duration,
  ) : MessageContent
}

/** For Timeline UI */
data class TimelineMessage(
    val id: MessageId,
    val sender: MessageSender,
    val quotedMessage: QuotedMessage?,
    val createdAt: Instant,
    val content: MessageContent,
)

/** For message preview */
data class MessagePreview(
    val type: MessageType,
    val text: String?,
    val senderId: UserId,
    val senderName: String?,
)

@Serializable
enum class MessageInputMode(val value: String) {
  Text("text"),
  @OptIn(ExperimentalSerializationApi::class) @JsonNames("Voice") HoldToRecord("hold-to-record"),
  VoiceNote("voice-note"),
  Attachment("attachment"),
  Camera("camera"),
}

@Serializable
data class VoiceDraft(
    val filename: String, // base 底稿音频文件路径
    val durationMs: Long, // base 底稿毫秒时长
    val waveformBase64: String, // base 底稿 5-bit 压缩后的波形转成 Base64 存储
    val pendingChunkFilename: String? = null, // 正在录制、待合并的新分片路径
) {
  // 辅助扩展属性，方便业务层直接使用
  val duration: Duration
    get() = durationMs.milliseconds

  val waveform: FloatArray
    get() = WaveformCodec5Bit.decode(waveformBase64.deserializeToByteArray())

  companion object {
    fun create(filename: String, duration: Duration, waveformBytes: ByteArray): VoiceDraft {
      return VoiceDraft(
          filename = filename,
          durationMs = duration.inWholeMilliseconds,
          waveformBase64 = Base64.encode(waveformBytes),
      )
    }
  }
}

/** For composer draft */
@Serializable
data class MessageDraft(
    val inputMode: MessageInputMode? = null,
    val quotedMessageId: MessageId? = null,
    val text: String = "",
    val voiceNote: VoiceDraft? = null,
)
