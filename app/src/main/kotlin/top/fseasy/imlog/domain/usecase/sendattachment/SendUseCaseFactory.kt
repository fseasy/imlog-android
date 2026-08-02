package top.fseasy.imlog.domain.usecase.sendattachment

import dagger.Lazy
import dagger.hilt.android.scopes.ViewModelScoped
import top.fseasy.imlog.domain.model.MessageType
import javax.inject.Inject

@ViewModelScoped
class SendUriUseCaseFactory
@Inject
constructor(
    private val sendAudioMessageUseCase: Lazy<SendAudioMessageUseCase>,
    private val sendImageMessageUseCase: Lazy<SendImageMessageUseCase>,
    private val sendVideoMessageUseCase: Lazy<SendVideoMessageUseCase>,
    private val sendGenericFileMessageUseCase: Lazy<SendGenericFileMessageUseCase>,
) {
  fun get(type: MessageType): SendUriUseCaseBase {
    return when (type) {
      MessageType.Audio -> sendAudioMessageUseCase.get()
      MessageType.Image -> sendImageMessageUseCase.get()
      MessageType.Video -> sendVideoMessageUseCase.get()
      MessageType.GenericFile -> sendGenericFileMessageUseCase.get()
      else -> error("MessageType $type doesn't have SendUriUseCase")
    }
  }
}

/** For common api: runBackground */
@ViewModelScoped
class SendRunBackgroundUseCaseFactory
@Inject
constructor(
    private val sendAudioMessageUseCase: Lazy<SendAudioMessageUseCase>,
    private val sendImageMessageUseCase: Lazy<SendImageMessageUseCase>,
    private val sendVideoMessageUseCase: Lazy<SendVideoMessageUseCase>,
    private val sendGenericFileMessageUseCase: Lazy<SendGenericFileMessageUseCase>,
    private val sendVoiceMessageUseCase: Lazy<SendVoiceMessageUseCase>,
) {
  fun get(type: MessageType): SendUseCaseBase {
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
