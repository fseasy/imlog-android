package top.fseasy.imlog.domain.usecase.sendfilemessage.stage

import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.RetryModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.DbRunner
import top.fseasy.imlog.domain.repository.MessageFileSource
import top.fseasy.imlog.domain.repository.MessageRepository
import javax.inject.Inject

class InitializeFileMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val dbRunner: DbRunner,
) {

    /**
     * @throws Throwable
     */
    suspend fun initializeUriSourceFileMessage(
        srcUriStr: UriStr,
        senderId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        messageType: MessageType,
        fileMetadata: FileMetadataUnion,
    ): MessageId = dbRunner.runInTransaction(retry = RetryModel.OnAnyException) {
        val messageId = messageRepository.syncInsertInitialFileMessage(
            topicId = topicId,
            senderId = senderId,
            type = messageType,
            timestampMs = messageTimestampMs,
            fileMetadata = fileMetadata
        )
        messageRepository.syncInsertInitialFileProcessingTaskState(
            messageId = messageId,
            fileSource = MessageFileSource.FromUriStr(srcUriStr),
            taskStartTime = messageTimestampMs
        )
        messageId
    }

    /**
     * @param cacheFilename: obey the @StoragePathUseCase.buildMessageCacheFileStoragePath
     * @throws Throwable
     */
    suspend fun initializeCacheFileSourceFileMessage(
        cacheFilename: String,
        senderId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        messageType: MessageType,
        fileMetadata: FileMetadataUnion,
    ): MessageId = dbRunner.runInTransaction(retry = RetryModel.OnAnyException) {
        val messageId = messageRepository.syncInsertInitialFileMessage(
            topicId = topicId,
            senderId = senderId,
            type = messageType,
            timestampMs = messageTimestampMs,
            fileMetadata = fileMetadata
        )
        messageRepository.syncInsertInitialFileProcessingTaskState(
            messageId = messageId,
            fileSource = MessageFileSource.FromMessageCacheFile(cacheFilename),
            taskStartTime = messageTimestampMs
        )
        messageId
    }
}