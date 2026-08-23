package top.fseasy.imlog.domain.repository

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.MessageDraft
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicAvatarModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPreference
import top.fseasy.imlog.domain.model.UserId

interface TopicRepository {
  /**
   * Get Topic Flow, catch exception and return null.
   *
   * Run in IO.
   */
  fun observeTopicOrNull(topicId: TopicId): Flow<Topic?>

  fun observeTopicPreferenceOrNull(userId: UserId, topicId: TopicId): Flow<TopicPreference?>

  fun observeHomeTopics(userId: UserId): Flow<List<HomeTopic>>

  /**
   * Use this if you need some extra operation after/before create topic In Transaction guarantee.
   * Need wrap it in withContext(IO) and transaction!!
   *
   * @throws Exception
   */
  fun syncCreateNewTopic(
      creatorId: UserId,
      name: String,
      avatarModel: TopicAvatarModel,
      description: String?,
      createdAt: Instant = Clock.System.now(),
  ): TopicId

  /**
   * Run in IO and transaction. Use this if you just need to create a new topic without any other db
   * operations
   *
   * @throws Exception
   */
  suspend fun createNewTopic(
      creatorId: UserId,
      name: String,
      avatarModel: TopicAvatarModel,
      description: String?,
      createdAt: Instant = Clock.System.now(),
  ): TopicId

  suspend fun countAllRelatedTopicsForUser(userId: UserId): Long

  suspend fun updateTopicName(userId: UserId, topicId: TopicId, newName: String): Boolean

  suspend fun updateAvatarModel(
      userId: UserId,
      topicId: TopicId,
      newAvatarModel: TopicAvatarModel,
  ): Boolean

  suspend fun updateTopicBackground(
      userId: UserId,
      topicId: TopicId,
      background: String?,
  ): Boolean

  suspend fun deleteTopic(userId: UserId, topicId: TopicId): Boolean

  suspend fun archiveTopic(userId: UserId, topicId: TopicId, archived: Boolean): Boolean

  suspend fun pinTopic(userId: UserId, topicId: TopicId, pinned: Boolean): Boolean

  // =======
  // | Message Drafts
  // =======
  suspend fun getMessageDraft(userId: UserId, topicId: TopicId): MessageDraft?

  /** Run in IO thread. */
  suspend fun setMessageDraft(userId: UserId, topicId: TopicId, draft: MessageDraft?): Boolean
}
