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

@Immutable data class MessageSnippet(val header: String, val content: String)

@Immutable
data class TopicUiModel(
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
): TopicUiModel {
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
          lastMessagePreview,
          draft = draft,
          description = description,
          context = context,
      )
  return TopicUiModel(
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
    latestMessagePreview: MessagePreview?,
    draft: MessageDraft?,
    description: String?,
    context: Context,
): MessageSnippet {
  val draftSource = draft?.toMessageSnippet(context)
  if (draftSource != null) {
    return draftSource
  }
  val messageSource = latestMessagePreview?.toMessageSnippet(context)
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
fun MessagePreview.toMessageSnippet(context: Context): MessageSnippet {
  val content = buildString {
    if (senderName != null) {
      append(senderName)
      append(":")
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
      append(" [${context.getString(typeNoteResId)}]")
    }
    if (text != null) {
      append(" $text")
    }
  }
  return MessageSnippet(header = "", content = content)
}
