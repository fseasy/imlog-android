package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPersonalState
import top.fseasy.imlog.domain.model.TopicWithPersonalState
import top.fseasy.imlog.domain.model.UserId

interface TopicRepository {
    fun observeTopic(topicId: TopicId): Flow<Topic?>
    fun observeTopicPersonalState(userId: UserId, topicId: TopicId): Flow<TopicPersonalState?>
    fun observeHomeTopics(userId: UserId): Flow<List<HomeTopic>>
    fun observeTopicWithPersonalState(
        topicId: TopicId,
        userId: UserId,
    ): Flow<TopicWithPersonalState?>


    // ----- SYNC Groups. It needs outer's IO threads and transaction protection!
    // ----- DON't USE `suspend` to keep it be called correctly
    /**
     * Use this if you need some extra operation after/before create topic In Transaction guarantee.
     * Need wrap it in withContext(IO) and transaction!!
     * @throws Exception
     */
    fun syncCreateNewTopic(
        creatorId: UserId, name: String, avatarModel: AvatarModel, description: String?,
        createdAtTimestampMs: Long = System.currentTimeMillis(),
    ): TopicId
    // ----- End of SYNC groups

    /***
     * Run in IO and transaction.
     * Use this if you just need to create a new topic without any other db operations
     * @throws Exception
     */
    suspend fun createNewTopic(
        creatorId: UserId, name: String, avatarModel: AvatarModel, description: String?,
        createdAtTimestampMs: Long = System.currentTimeMillis(),
    ): TopicId

    suspend fun countAllRelatedTopicsForUser(userId: UserId): Long

    suspend fun updateTopicName(userId: UserId, topicId: TopicId, newName: String): Boolean
    suspend fun updateAvatarModel(
        userId: UserId,
        topicId: TopicId,
        newAvatarModel: AvatarModel,
    ): Boolean

    suspend fun updateTopicBackground(
        userId: UserId,
        topicId: TopicId,
        background: String?,
    ): Boolean

    suspend fun deleteTopic(userId: UserId, topicId: TopicId): Boolean
    suspend fun archiveTopic(userId: UserId, topicId: TopicId, archived: Boolean): Boolean
    suspend fun pinTopic(userId: UserId, topicId: TopicId, pinned: Boolean): Boolean

}