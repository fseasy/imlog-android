package top.fseasy.imlog.data.model

import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.Serializable
import top.fseasy.imlog.domain.audio.WaveformCodec5Bit
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.model.VoiceDraftDetail
import top.fseasy.imlog.domain.usecase.composer.VoiceDraftPathUseCase

@Serializable
data class VoiceDraftDetailEntity(
    val filename: String,
    val durationMs: Long,
    val waveform5BitBase64: String, // 持久化用紧凑格式
)

@Serializable
data class VoiceDraftEntity(
    val base: VoiceDraftDetailEntity?,
    val pendingChunkFilename: String? = null,
)

fun VoiceDraftDetail.toEntity(): VoiceDraftDetailEntity {
  val packedBytes = WaveformCodec5Bit.pack(waveform)
  return VoiceDraftDetailEntity(
      filename = VoiceDraftPathUseCase.getRestorableDraftFilename(filePath),
      durationMs = duration.inWholeMilliseconds,
      waveform5BitBase64 = Base64.encode(packedBytes),
  )
}

fun VoiceDraft.toEntity(): VoiceDraftEntity {
  return VoiceDraftEntity(
      base = base?.toEntity(),
      pendingChunkFilename =
          pendingChunkFilePath?.let { VoiceDraftPathUseCase.getRestorableDraftFilename(it) },
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

fun VoiceDraftEntity.toDomain(
    draftPathUseCase: VoiceDraftPathUseCase,
    userId: UserId,
): VoiceDraft {
  return VoiceDraft(
      base = base?.toDomain(draftPathUseCase, userId),
      pendingChunkFilePath =
          pendingChunkFilename?.let { draftPathUseCase.restoreDraftPath(userId, filename = it) },
  )
}
