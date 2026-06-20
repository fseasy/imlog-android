package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPersonalState
import top.fseasy.imlog.domain.model.TopicWithPersonalState
import top.fseasy.imlog.domain.model.UserId

interface TopicRepository {
    fun observeTopic(topicId: TopicId): Flow<Topic?>
    fun observeTopicPersonalState(userId: UserId, topicId: TopicId): Flow<TopicPersonalState?>
    fun observeLogScreenTopics(userId: UserId): Flow<List<HomeTopic>>

    suspend fun createTopic(creatorId: UserId, name: String, iconUri: String?): HomeTopic

    suspend fun updateTopicName(userId: UserId, topicId: TopicId, newName: String): Boolean
    suspend fun updateTopicIcon(userId: UserId, topicId: TopicId, newIconUri: String): Boolean
    suspend fun updateTopicBackground(userId: UserId, topicId: TopicId, background: String?): Boolean

    suspend fun deleteTopic(userId: UserId, topicId: TopicId): Boolean
    suspend fun archiveTopic(userId: UserId, topicId: TopicId, archived: Boolean): Boolean
    suspend fun pinTopic(userId: UserId, topicId: TopicId, pinned: Boolean): Boolean
    fun observeTopicWithPersonalState(
        topicId: TopicId,
        userId: UserId
    ): Flow<TopicWithPersonalState?>

    suspend fun countAllRelatedTopicsForUser(userId: UserId): Long
}