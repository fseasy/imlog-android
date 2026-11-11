// domain/model/VoiceDraft.kt
package top.fseasy.imlog.domain.model

import kotlin.time.Duration
import top.fseasy.imlog.domain.audio.Waveform

data class VoiceDraftDetail(
    val filePath: StoragePathModel.Internal,
    val duration: Duration,
    val waveform: Waveform, // 直接是规范化的浮点波形对象
)

data class VoiceDraft(
    val base: VoiceDraftDetail?,
    val pendingChunkFilePath: StoragePathModel.Internal? = null,
)
