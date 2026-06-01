package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.LogScreenTopic
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicPersonalState

interface TopicRepository {
    fun observeTopicById(topicId: String): Flow<Topic>
    fun observeTopicPersonalStateById(userId: String, topicId: String): Flow<TopicPersonalState>
    fun observeLogScreenTopics(userId: String): Flow<List<LogScreenTopic>>

    suspend fun createTopic(creatorId: String, name: String, iconUri: String?): LogScreenTopic

    suspend fun updateTopicName(userId: String, topicId: String, newName: String): Boolean
    suspend fun updateTopicIcon(userId: String, topicId: String, newIconUri: String): Boolean
    suspend fun updateTopicBackground(userId: String, topicId: String, background: String?): Boolean

    suspend fun deleteTopic(userId: String, topicId: String): Boolean
    suspend fun archiveTopic(userId: String, topicId: String, archived: Boolean): Boolean
    suspend fun pinTopic(userId: String, topicId: String, pinned: Boolean): Boolean
}