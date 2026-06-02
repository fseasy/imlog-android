package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.LogScreenTopic
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPersonalState
import top.fseasy.imlog.domain.model.UserId

interface TopicRepository {
    fun observeTopicById(topicId: TopicId): Flow<Topic?>
    fun observePersonalStateById(userId: UserId, topicId: TopicId): Flow<TopicPersonalState?>
    fun observeLogScreenTopics(userId: UserId): Flow<List<LogScreenTopic>>

    suspend fun createTopic(creatorId: UserId, name: String, iconUri: String?): LogScreenTopic

    suspend fun updateTopicName(userId: UserId, topicId: TopicId, newName: String): Boolean
    suspend fun updateTopicIcon(userId: UserId, topicId: TopicId, newIconUri: String): Boolean
    suspend fun updateTopicBackground(userId: UserId, topicId: TopicId, background: String?): Boolean

    suspend fun deleteTopic(userId: UserId, topicId: TopicId): Boolean
    suspend fun archiveTopic(userId: UserId, topicId: TopicId, archived: Boolean): Boolean
    suspend fun pinTopic(userId: UserId, topicId: TopicId, pinned: Boolean): Boolean
}