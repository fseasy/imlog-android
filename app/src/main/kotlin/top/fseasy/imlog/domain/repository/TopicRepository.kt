package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.Topic

interface TopicRepository {
    fun getAllActiveTopics(): Flow<List<Topic>>

    suspend fun createTopic(name: String, creatorId: String): Topic

    suspend fun updateTopic(topicId: String, name: String, iconUri: String?)

    suspend fun deleteTopic(topicId: String)

    suspend fun pinTopic(topicId: String, userId: String, pinned: Boolean)

    suspend fun archiveTopic(topicId: String, userId: String, archived: Boolean)

    suspend fun setTopicFont(topicId: String, font: String?)

    suspend fun setTopicBackground(topicId: String, userId: String, background: String?)
    fun getTopic(topicId: String): Flow<Topic?>
}