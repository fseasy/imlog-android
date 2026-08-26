package top.fseasy.imlog.features.home.topiclog.composer

import android.content.Context
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.data.util.VoiceRecorder
import top.fseasy.imlog.data.util.VoiceRecorderState
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.SendVoiceMessageUseCase

class VoiceRecordingUiState(
    val recorderState: VoiceRecorderState = VoiceRecorderState.Idle,
    val elapsed: Duration = 0.milliseconds,
)

/**
 * State Holder
 *
 * @param coroutineScope should be bound viewModelScope
 */
class VoiceRecorderStateHolder(
    coroutineScope: CoroutineScope,
    private val storagePathUseCase: StoragePathUseCase,
    private val sendVoiceMessageUseCase: SendVoiceMessageUseCase,
    private val context: Context,
) : AutoCloseable {

  val voiceRecorder = VoiceRecorder(coroutineScope)

  val voiceRecordingUiState: StateFlow<VoiceRecordingUiState> =
      combine(
              voiceRecorder.state,
              voiceRecorder.elapsedMs,
          ) { state, elapsedMs ->
            VoiceRecordingUiState(state, elapsedMs.milliseconds)
          }
          .stateIn(
              scope = coroutineScope,
              started = SharingStarted.WhileSubscribed(5_000),
              initialValue = VoiceRecordingUiState(),
          )

  suspend fun startVoiceRecording(userId: UserId): Result<Unit> {
    val path = generateVoiceRecordingOutputPathInMessageCacheRule(userId = userId)
    return voiceRecorder.start(context, path)
  }

  suspend fun cancelVoiceRecording() {
    voiceRecorder.cancel()
  }

  suspend fun stopVoiceRecordingAndSendVoiceMessage(topicId: TopicId, userId: UserId) {
    voiceRecorder.stop()?.let {
      // It follows the message cache file generating rule, so only filename is necessary
      sendVoiceMessageUseCase(
          it.name,
          userId = userId,
          topicId = topicId,
          messageTimestamp = Clock.System.now(),
      )
    }
  }

  private fun generateVoiceRecordingOutputPathInMessageCacheRule(
      userId: UserId,
      now: Instant = Clock.System.now(),
  ): java.nio.file.Path {
    val filename =
        storagePathUseCase.buildTimestampedFilename(
            now,
            originalFilename = VoiceRecorder.generateOutputAudioDefaultFilename("voice"),
        )
    val outputFilePath =
        storagePathUseCase.buildMessageCacheFileStoragePath(
            userId = userId,
            filename = filename,
        )
    return outputFilePath.toNioPath(context)
  }

  override fun close() {
    voiceRecorder.close()
  }
}
