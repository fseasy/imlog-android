package top.fseasy.imlog.domain.usecase.composer.sendattachment

import dagger.Lazy
import top.fseasy.imlog.domain.model.MessageType
import javax.inject.Inject


/** For common api: runBackground.
 * NOTE: no @ViewModelScoped as it's running in background and independent of UI*/
class SendAttachmentUseCaseFactory
@Inject
constructor(
    private val sendAudioMessageUseCase: Lazy<SendAudioMessageUseCase>,
    private val sendImageMessageUseCase: Lazy<SendImageMessageUseCase>,
    private val sendVideoMessageUseCase: Lazy<SendVideoMessageUseCase>,
    private val sendGenericFileMessageUseCase: Lazy<SendGenericFileMessageUseCase>,
    private val sendVoiceMessageUseCase: Lazy<SendVoiceMessageUseCase>,
) {
  fun get(type: MessageType): SendAttachmentUseCaseBase {
    return when (type) {
      MessageType.Audio -> sendAudioMessageUseCase.get()
      MessageType.Image -> sendImageMessageUseCase.get()
      MessageType.Video -> sendVideoMessageUseCase.get()
      MessageType.GenericFile -> sendGenericFileMessageUseCase.get()
      MessageType.Voice -> sendVoiceMessageUseCase.get()
      else -> error("MessageType $type doesn't have SendUriUseCase")
    }
  }
}
