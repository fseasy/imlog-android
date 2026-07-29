package top.fseasy.imlog.features.home.model

import android.os.Parcelable
import kotlinx.parcelize.DataClass
import kotlinx.parcelize.Experimental
import kotlinx.parcelize.Parcelize
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageInputMode
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.QuoteMessage
import top.fseasy.imlog.domain.model.QuoteMessageThumbnailFileBuildingArgs
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendAudioMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendGenericFileMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendImageMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendVideoMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendVoiceMessageUseCase
import javax.inject.Inject


class SendFileMessageUseCases @Inject constructor(
    val sendAudio: SendAudioMessageUseCase,
    val sendVoice: SendVoiceMessageUseCase,
    val sendVideo: SendVideoMessageUseCase,
    val sendImage: SendImageMessageUseCase,
    val sendGenericFile: SendGenericFileMessageUseCase,
)

/**
 * Currently we
 */
@Parcelize
enum class MessageInputModeParcelable : Parcelable {
    Text, Voice, Attachment
}

fun MessageInputMode.toParcelable(): MessageInputModeParcelable = when (this) {
    MessageInputMode.Text -> MessageInputModeParcelable.Text
    MessageInputMode.Voice -> MessageInputModeParcelable.Voice
    MessageInputMode.Attachment -> MessageInputModeParcelable.Attachment
}


fun MessageInputModeParcelable.toDomain(): MessageInputMode = when (this) {
    MessageInputModeParcelable.Text -> MessageInputMode.Text
    MessageInputModeParcelable.Voice -> MessageInputMode.Voice
    MessageInputModeParcelable.Attachment -> MessageInputMode.Attachment
}


@Parcelize
data class QuoteMessageParcelable @OptIn(Experimental::class) constructor(
    val id: String,
    val senderId: String,
    val senderNameSnapshot: String,
    val messageType: String,
    val text: String,
    val thumbnail: @DataClass QuoteMessageThumbnailFileBuildingArgs?,
) : Parcelable

fun QuoteMessage.toParcelable() = QuoteMessageParcelable(
    id = id.value,
    senderId = senderId.value,
    senderNameSnapshot = senderNameSnapshot,
    messageType = messageType.value,
    text = text,
    thumbnail = thumbnail
)

fun QuoteMessageParcelable.toDomain() = QuoteMessage(
    id = MessageId(id),
    senderId = UserId(senderId),
    senderNameSnapshot = senderNameSnapshot,
    messageType = MessageType.fromValue(messageType) ?: MessageType.Text,
    text = text,
    thumbnail = thumbnail,
)

/**
 * Will be saved to SavedStateHandle, needs the parcelize interface.
 * What's Meta: includes all the attributes of drafts except the inputText.
 *  As it's high frequent fresh data, will be processed alone.
 */
@Parcelize
sealed interface ComposerDraftMeta : Parcelable {
    @Parcelize
    data object Loading : ComposerDraftMeta


    @Parcelize
    data class Ready(
        val inputMode: MessageInputModeParcelable?,
        val quoteMessage: QuoteMessageParcelable?,
    ) : ComposerDraftMeta
}

sealed interface ComposerUiEffect {
    object HideKeyboard : ComposerUiEffect
    object PopBackStack : ComposerUiEffect
    object Vibrate : ComposerUiEffect
}