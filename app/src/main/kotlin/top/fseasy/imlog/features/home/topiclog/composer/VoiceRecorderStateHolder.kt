package top.fseasy.imlog.features.home.topiclog.composer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import top.fseasy.imlog.data.mapper.toFileWithCreatingDirectories
import top.fseasy.imlog.data.util.VoiceRecorder
import top.fseasy.imlog.data.util.VoiceRecorderState
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.SendVoiceMessageUseCase
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

    val voiceRecordingUiState: StateFlow<VoiceRecordingUiState> = combine(
        voiceRecorder.state, voiceRecorder.elapsedMs
    ) { state, elapsedMs ->
        VoiceRecordingUiState(state, elapsedMs.milliseconds)
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VoiceRecordingUiState()
    )

    suspend fun startVoiceRecording(userId: UserId) {
        val outputFile = generateVoiceRecordingOutputFileInMessageCacheRule(userId = userId)
        voiceRecorder.start(context, outputFile)
    }

    suspend fun cancelVoiceRecording() {
        voiceRecorder.cancel()
    }

    suspend fun stopVoiceRecordingAndSendVoiceMessage(topicId: TopicId, userId: UserId) {
        voiceRecorder.stop()
            ?.let {
                // It follows the message cache file generating rule, so only filename is necessary
                sendVoiceMessageUseCase(
                    it.name,
                    userId = userId,
                    topicId = topicId,
                    messageTimestampMs = System.currentTimeMillis(),
                )
            }
    }

    private suspend fun generateVoiceRecordingOutputFileInMessageCacheRule(
        userId: UserId,
        now: Long = System.currentTimeMillis(),
    ): File {
        val filename = storagePathUseCase.buildTimestampedFilename(
            now, originalFilename = VoiceRecorder.generateOutputAudioDefaultFilename("voice")
        )
        val outputFilePath = storagePathUseCase.buildMessageCacheFileStoragePath(
            userId = userId, filename = filename
        )
        val outputFile = outputFilePath.toFileWithCreatingDirectories(context)
        return outputFile
    }

    override fun close() {
        voiceRecorder.close()
    }
}