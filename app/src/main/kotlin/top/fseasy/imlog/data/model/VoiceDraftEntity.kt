package top.fseasy.imlog.data.model

import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.Serializable
import timber.log.Timber
import top.fseasy.imlog.data.commonmain.audio.WaveformCodec5Bit
import top.fseasy.imlog.domain.model.LoadedVoiceDraft
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.model.VoiceDraftDetail
import top.fseasy.imlog.domain.usecase.pathpolicy.VoiceDraftPathUseCase

@Serializable
data class VoiceDraftDetailEntity(
    val filename: String,
    val durationMs: Long,
    val waveform5BitBase64: String, // 持久化用紧凑格式
)

@Serializable
data class VoiceDraftEntity(
    val base: VoiceDraftDetailEntity?,
    val pendingSegmentFilename: String? = null,
)

fun VoiceDraftDetail.toEntity(): VoiceDraftDetailEntity {
  val packedBytes = WaveformCodec5Bit.pack(waveform)
  return VoiceDraftDetailEntity(
      filename = VoiceDraftPathUseCase.getRestorableDraftFilename(filePath),
      durationMs = duration.inWholeMilliseconds,
      waveform5BitBase64 = Base64.encode(packedBytes),
  )
}

fun VoiceDraft.toEntity() =
    when (this) {
      is VoiceDraft.ActiveRecording.Appending ->
          VoiceDraftEntity(
              base = base.toEntity(),
              pendingSegmentFilename =
                  VoiceDraftPathUseCase.getRestorableDraftFilename(recordingFilePath),
          )
      is VoiceDraft.ActiveRecording.Base ->
          VoiceDraftEntity(
              base = null,
              pendingSegmentFilename =
                  VoiceDraftPathUseCase.getRestorableDraftFilename(recordingFilePath),
          )
      is VoiceDraft.Committed ->
          VoiceDraftEntity(
              base = detail.toEntity(),
              pendingSegmentFilename = null,
          )
    }

fun VoiceDraftDetailEntity.toDomain(
    draftPathUseCase: VoiceDraftPathUseCase,
    userId: UserId,
): VoiceDraftDetail {
  val packedBytes = Base64.decode(waveform5BitBase64)
  return VoiceDraftDetail(
      filePath = draftPathUseCase.restoreDraftPath(userId, filename = filename),
      duration = durationMs.milliseconds,
      waveform = WaveformCodec5Bit.unpack(packedBytes),
  )
}

/** No exception. */
fun VoiceDraftEntity.toLoadedVoiceDraft(
    draftPathUseCase: VoiceDraftPathUseCase,
    userId: UserId,
): LoadedVoiceDraft {
  val recordingFilePath = pendingSegmentFilename?.let { filename ->
    draftPathUseCase.restoreDraftPath(userId, filename)
  }
  if (base != null) {
    try {
      val baseDomain = base.toDomain(draftPathUseCase, userId)
      return LoadedVoiceDraft.Valid(buildVoiceDraftWithBase(baseDomain, recordingFilePath))
    } catch (e: Throwable) {
      Timber.w(e, "VoiceDraft: unpack waveform failed")
      return LoadedVoiceDraft.Corrupted.WaveformUnpack(
          baseFilePath = draftPathUseCase.restoreDraftPath(userId, base.filename),
          baseDuration = base.durationMs.milliseconds,
          recordingFilePath = recordingFilePath,
      )
    }
  } else {
    return if (recordingFilePath != null) {
      LoadedVoiceDraft.Valid(VoiceDraft.ActiveRecording.Base(recordingFilePath))
    } else {
      LoadedVoiceDraft.None
    }
  }
}

private fun buildVoiceDraftWithBase(
    baseDetail: VoiceDraftDetail,
    recordingFilePath: StoragePathModel.Internal?,
): VoiceDraft =
    if (recordingFilePath != null) {
      VoiceDraft.ActiveRecording.Appending(
          base = baseDetail,
          recordingFilePath = recordingFilePath,
      )
    } else {
      VoiceDraft.Committed(detail = baseDetail)
    }
