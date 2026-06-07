package top.fseasy.imlog.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.file.MediaSaveResult
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId

interface MessageRepository {
    fun observeTopicMessages(topicId: TopicId, currentUserId: UserId): Flow<List<Message>>
    fun observeStatistics(senderId: UserId): Flow<Statistics>
    suspend fun save(message: Message): Unit
    suspend fun delete(messageId: MessageId): Boolean
    suspend fun sendMediaMessage(
        topicId: TopicId,
        senderId: UserId,
        messageType: MessageType,
        mediaUri: Uri,
    ): Unit

    suspend fun finishMediaProcessing(messageId: MessageId, savedMedia: MediaSaveResult.SavedMedia)
}