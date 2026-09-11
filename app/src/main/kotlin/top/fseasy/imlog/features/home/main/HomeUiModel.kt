package top.fseasy.imlog.features.home.main

import android.content.Context
import androidx.compose.runtime.Immutable
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.MessageDraft
import top.fseasy.imlog.domain.model.MessagePreview
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.ui.model.TopicAvatarUiModel
import top.fseasy.imlog.ui.model.buildTopicAvatarNioPath
import top.fseasy.imlog.ui.model.toUiModel
import top.fseasy.imlog.ui.util.ImTimeUtils
import top.fseasy.imlog.ui.util.toJavaInstant

/** Used in Home Top Bar More Menu */
@Immutable
data class MoreOptionMenuAction(
    val onCreateTopic: () -> Unit,
    val onOpenAppSettings: () -> Unit,
)

/** For message snippet showing in home topic item */
@Immutable data class MessageSnippet(val header: String, val content: String)

/** Each Home Topic item */
@Immutable
data class HomeTopicUiModel(
    val id: TopicId,
    val name: String,
    val avatarUiModel: TopicAvatarUiModel,
    val isPinned: Boolean,
    val hasUnread: Boolean,
    val messageFormatedUpdatedAt: String,
    val messageSnippet: MessageSnippet,
)

fun HomeTopic.toUiModel(
    currentUserId: UserId,
    storagePathUseCase: StoragePathUseCase,
    context: Context,
): HomeTopicUiModel {
  val avatarUiModel = avatarModel.toUiModel { filename ->
    buildTopicAvatarNioPath(
        signInUserId = currentUserId,
        storagePathUseCase = storagePathUseCase,
        context = context,
        filename = filename,
    )
  }
  val messageSnippet =
      buildMessageSnippet(
          currentUserId = currentUserId,
          lastMessagePreview = lastMessagePreview,
          draft = draft,
          description = description,
          context = context,
      )
  return HomeTopicUiModel(
      id = id,
      name = name,
      avatarUiModel = avatarUiModel,
      isPinned = isPinned,
      hasUnread = hasUnread,
      messageFormatedUpdatedAt = ImTimeUtils.formatImTime(messageUpdatedAt.toJavaInstant()),
      messageSnippet = messageSnippet,
  )
}

private fun buildMessageSnippet(
    currentUserId: UserId,
    lastMessagePreview: MessagePreview?,
    draft: MessageDraft?,
    description: String?,
    context: Context,
): MessageSnippet {
  val draftSource = draft?.toMessageSnippet(context)
  if (draftSource != null) {
    return draftSource
  }
  val messageSource = lastMessagePreview?.toMessageSnippet(currentUserId, context)
  if (messageSource != null) {
    return messageSource
  }
  if (description != null) {
    return MessageSnippet(header = "", content = description)
  }
  return MessageSnippet("", "")
}

/** Mimic WeChat logic */
fun MessageDraft.toMessageSnippet(context: Context): MessageSnippet? {
  val header = "[${context.getString(R.string.home_topic_message_snippet_draft_header)}]"
  return if (text.isBlank()) {
    if (quotedMessageId != null) {
      MessageSnippet(header = header, content = "")
    } else {
      null
    }
  } else {
    MessageSnippet(header = header, content = text)
  }
}

/** Logically, it can't generate an empty snippet */
fun MessagePreview.toMessageSnippet(
    currentUserId: UserId,
    context: Context,
): MessageSnippet {
  val parts = mutableListOf<String>()
  if (senderId != currentUserId && senderName != null) {
    parts.add("$senderName:")
  }
  val typeNoteResId =
      when (type) {
        MessageType.Text -> null
        MessageType.Image -> R.string.home_topic_message_snippet_image_type_note
        MessageType.Video -> R.string.home_topic_message_snippet_video_type_note
        MessageType.Audio -> R.string.home_topic_message_snippet_Audio_type_note
        MessageType.Voice -> R.string.home_topic_message_snippet_voice_type_note
        MessageType.GenericFile -> R.string.home_topic_message_snippet_generic_file_type_note
      }
  if (typeNoteResId != null) {
    parts.add("[${context.getString(typeNoteResId)}]")
  }
  if (text != null) {
    // Trim spaces of message
    parts.add(text.trim())
  }
  return MessageSnippet(header = "", content = parts.joinToString(" "))
}
