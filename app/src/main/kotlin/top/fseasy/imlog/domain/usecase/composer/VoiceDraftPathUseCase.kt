package top.fseasy.imlog.domain.usecase.composer

import javax.inject.Inject
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.composer.sendattachment.AttachmentPathUseCase

/**
 * It must be lightweight enough as it will be used in VoiceDraft domain-data mapping logic. so we
 * extract it as a single usecase
 */
class VoiceDraftPathUseCase
@Inject
constructor(private val attachmentPathUseCase: AttachmentPathUseCase) {
  /**
   * VoiceDraft file location follows the attachment path rule. Since it's related to the attachment
   * processing flow, put it here to keep things cohesive.
   */
  fun newVoiceDraftPath(
      userId: UserId,
      messageTimestamp: kotlin.time.Instant,
      audioFileExtension: String,
  ): StoragePathModel.Internal {
    val filename =
        attachmentPathUseCase.generateInternalFilename(
            messageTimestamp,
            "voice${audioFileExtension}",
        )
    return attachmentPathUseCase.generateInternalCachePath(userId, filename)
  }

  fun restoreDraftPath(userId: UserId, filename: String) =
      attachmentPathUseCase.generateInternalCachePath(userId, filename)

  companion object {
    fun getRestorableDraftFilename(draftPath: StoragePathModel.Internal): String =
        draftPath.fullRelativePath.last() // assert it's valid
  }
}
