package top.fseasy.imlog.domain.usecase.pathpolicy

import javax.inject.Inject
import kotlin.time.Instant
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId

/**
 * It must be lightweight enough as it will be used in VoiceDraft domain-data mapping logic. so we
 * extract it as a single usecase.
 *
 * NOT singleton yet as it's relatively not-common.
 */
class VoiceDraftPathUseCase
@Inject
constructor(
    private val attachmentPathUseCase: AttachmentPathUseCase,
    private val storagePathUseCase: StoragePathUseCase,
) {
  /**
   * VoiceDraft file location follows the attachment path rule. Since it's related to the attachment
   * processing flow, put it here to keep things cohesive.
   */
  fun newVoiceDraftPath(
      userId: UserId,
      messageTimestamp: Instant,
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

  /**
   * We just copy(move) the broken audio to the shared storage, so user can keep it by themselves.
   */
  fun newQuarantinePath(userId: UserId, filename: String): StoragePathModel.Shared {
    return storagePathUseCase.buildQuarantinePath(userId, filename)
  }

  companion object {
    fun getRestorableDraftFilename(draftPath: StoragePathModel.Internal): String =
        draftPath.fullRelativePath.last() // assert it's valid
  }
}
