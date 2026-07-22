package top.fseasy.imlog.domain.usecase.sendfilemessage.stage

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.domain.model.FileDeleteResult
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import javax.inject.Inject

class FinishProcessingUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val storageRepository: StorageRepository,
) {
    /**
     * 1. delete cache file 2. delete file-processing task record from db
     *
     * No exception will be thrown.
     *
     * Run in IO in each io parts.
     */
    suspend fun onSuccess(
        messageId: MessageId,
        internalCachePathModel: StoragePathModel.InternalOnly,
    ): FinishProcessingStageResult {
        when (storageRepository.deleteFile(internalCachePathModel)) {
            is FileDeleteResult.FileNotExist,
            is FileDeleteResult.Success,
                -> Unit

            is FileDeleteResult.Error -> return FinishProcessingStageResult.Failure(FinishProcessingStageFailureType.DeleteCacheFile)
        }

        try {
            messageRepository.deleteFileProcessingTaskState(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.i(e, "delete file processing task state failed")
            return FinishProcessingStageResult.Failure(FinishProcessingStageFailureType.DeleteTaskStateFromDb)
        }
        return FinishProcessingStageResult.Success
    }

    /**
     * Set failed in task db.
     *
     * No exception will be thrown, as this is the final step and there is no further handler to process it.
     */
    suspend fun onFailure(
        messageId: MessageId,
        stage: MessageProcessingErrorStage,
        errorUserRetryable: Boolean,
    ) {
        try {
            messageRepository.setFileProcessingTaskFail(
                messageId = messageId,
                stage = stage,
                errorUserRetryable = errorUserRetryable,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(
                e,
                "Failed to Set Message File Processing failure. Have to leave it to be uncertain status"
            )
            // can't do anything. Leave it to be
        }
    }
}


/**
 * Used in finishing task stage
 */
enum class FinishProcessingStageFailureType {
    DeleteCacheFile, DeleteTaskStateFromDb
}

sealed interface FinishProcessingStageResult {
    data object Success : FinishProcessingStageResult
    data class Failure(val type: FinishProcessingStageFailureType) : FinishProcessingStageResult
}