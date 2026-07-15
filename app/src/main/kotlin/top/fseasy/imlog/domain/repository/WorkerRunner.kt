package top.fseasy.imlog.domain.repository

import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId

interface WorkerRunner {
    suspend fun finishSendingAudio(
        messageId: MessageId,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        srcMetadata: AudioMetadata,
        cacheFilename: String,
    )
}