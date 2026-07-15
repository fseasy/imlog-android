package top.fseasy.imlog.worker

import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.WorkerRunner
import top.fseasy.imlog.domain.usecase.SendMessageFileUseCase
import javax.inject.Inject

class WorkerRunnerImpl @Inject constructor(
    private val sendFileMessageUseCase: SendMessageFileUseCase,
) : WorkerRunner {
    override suspend fun finishSendingAudio(
        messageId: MessageId,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        srcMetadata: AudioMetadata,
        cacheFilename: String,
    ) {
        // TODO

    }

}