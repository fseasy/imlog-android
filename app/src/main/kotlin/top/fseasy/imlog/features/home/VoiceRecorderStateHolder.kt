package top.fseasy.imlog.features.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.data.mapper.toFileWithCreatingDirectories
import top.fseasy.imlog.data.util.VoiceRecorder
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.SendVoiceMessageUseCase
import java.io.File

@HiltViewModel
class VoiceRecorderStateHolder @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val sendVoiceMessageUseCase: SendVoiceMessageUseCase,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    val voiceRecorder = VoiceRecorder(viewModelScope).also(::addCloseable)

    val voiceRecordingUiState: StateFlow<VoiceRecordingUiState> = combine(
        voiceRecorder.state, voiceRecorder.elapsedMs
    ) { state, elapsedMs ->
        VoiceRecordingUiState(state, elapsedMs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
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
}