package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId

interface MessageRepository {
    fun observeTopicMessages(topicId: TopicId): Flow<List<Message>>
    fun observeStatistics(senderId: UserId): Flow<Statistics>
    suspend fun save(message: Message): Unit
    suspend fun delete(messageId: MessageId): Boolean

}