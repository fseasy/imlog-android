package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.MessageDraft
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessagePreview
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicAvatarModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicMemberRole
import top.fseasy.imlog.domain.model.TopicPreference
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.sqldelight.GetCurrentUserHomeScreenTopics as HomeTopicEntity
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.sqldelight.Topic_members as TopicMemberEntity
import top.fseasy.imlog.sqldelight.Topic_message_state
import top.fseasy.imlog.sqldelight.Topic_preference as TopicPreferenceEntity
import top.fseasy.imlog.sqldelight.Topics as TopicEntity

@Singleton
class TopicRepositoryImpl
@Inject
constructor(
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher,
) : TopicRepository {

  override fun observeTopicOrNull(topicId: TopicId): Flow<Topic?> =
      safeObserveFlowOrNull({ "No Topic found for id=${topicId}" }) {
        database.topicSelectQueries.getTopicById(topicId).asFlow().mapToOneOrNull(dispatcher).map {
          it?.toDomain()
        }
      }

  override fun observeTopicPreferenceOrNull(
      userId: UserId,
      topicId: TopicId,
  ): Flow<TopicPreference?> =
      safeObserveFlowOrNull({ "Observe TopicPersonalState failed on id=${topicId}" }) {
        database.topicSelectQueries
            .getPreference(topic_id = topicId, user_id = userId)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }
      }

  /** Used for Log Screen Topics lists (home screen) */
  override fun observeHomeTopics(userId: UserId): Flow<List<HomeTopic>> {
    return database.topicSelectQueries
        .getCurrentUserHomeScreenTopics(userId)
        .asFlow()
        .mapToList(dispatcher)
        .map { rows -> rows.map { it.toDomain() } }
  }

  /** @see top.fseasy.imlog.domain.usecase.WelcomeUseCase */
  override suspend fun countAllRelatedTopicsForUser(userId: UserId): Long =
      withContext(dispatcher) {
        database.topicSelectQueries.countAllRelatedTopicsForUser(userId).executeAsOne()
      }

  /**
   * NOTE: it's SYNC. Not In IO thread, not in transaction. It's expected to be used in
   * withContext(IO) and transaction block!
   *
   * @throws Exception
   */
  override fun syncCreateNewTopic(
      creatorId: UserId,
      name: String,
      avatarModel: TopicAvatarModel,
      description: String?,
      createdAt: Instant,
  ): TopicId {
    val topicId = TopicId.random()
    val createdAtMs = createdAt.toEpochMilliseconds()

    // needs to insert to 3 tables: 1. topic 2. personal state 3. topic-members
    database.topicQueries.insertTopic(
        TopicEntity(
            id = topicId,
            name = name,
            avatar_model = avatarModel,
            description = description,
            creator_id = creatorId,
            created_at = createdAtMs,
            attributes_updated_at = createdAtMs,
            archived = false,
        )
    )
    database.topicQueries.insertPersonalPreference(
        TopicPreferenceEntity(
            topic_id = topicId,
            user_id = creatorId,
            pinned = false,
            background = null,
            attributes_updated_at = createdAtMs,
        )
    )
    database.topicQueries.insertMember(
        TopicMemberEntity(
            topic_id = topicId,
            user_id = creatorId,
            role = TopicMemberRole.Admin,
            joined_at = createdAtMs,
            attributes_updated_at = createdAtMs,
        )
    )
    database.topicQueries.insertMessageState(
        Topic_message_state(
            topic_id = topicId,
            user_id = creatorId,
            last_read_message_id = null,
            draft_quoted_message_id = null,
            draft_text_content = null,
            draft_voice_content = null,
        )
    )
    return topicId
  }

  override suspend fun createNewTopic(
      creatorId: UserId,
      name: String,
      avatarModel: TopicAvatarModel,
      description: String?,
      createdAt: Instant,
  ): TopicId =
      withContext(dispatcher) {
        retrySQLiteOnKeyConflict {
          database.transactionWithResult {
            syncCreateNewTopic(
                creatorId = creatorId,
                name = name,
                avatarModel = avatarModel,
                description = description,
                createdAt = createdAt,
            )
          }
        }
      }

  override suspend fun updateTopicName(
      userId: UserId,
      topicId: TopicId,
      newName: String,
  ): Boolean =
      withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected =
            database.topicUpdateQueries
                .updateTopicName(
                    newName = newName,
                    updatedAt = now,
                    topicId = topicId,
                    triggerUserId = userId,
                    adminRoleValue = TopicMemberRole.Admin,
                )
                .value
        rowsAffected > 0L
      }

  override suspend fun updateAvatarModel(
      userId: UserId,
      topicId: TopicId,
      newAvatarModel: TopicAvatarModel,
  ): Boolean =
      withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected =
            database.topicUpdateQueries
                .updateTopicAvatarModel(
                    newAvatarModel = newAvatarModel,
                    updatedAt = now,
                    topicId = topicId,
                    triggerUserId = userId,
                    adminRoleValue = TopicMemberRole.Admin,
                )
                .value
        rowsAffected > 0L
      }

  override suspend fun updateTopicBackground(
      userId: UserId,
      topicId: TopicId,
      background: String?,
  ): Boolean =
      withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected =
            database.topicUpdateQueries
                .updateTopicPersonalBackground(
                    newBackground = background,
                    updatedAt = now,
                    topicId = topicId,
                    triggerUserId = userId,
                )
                .value
        rowsAffected > 0L
      }

  override suspend fun pinTopic(userId: UserId, topicId: TopicId, pinned: Boolean): Boolean =
      withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected =
            database.topicUpdateQueries
                .updateTopicPersonalPinned(
                    newPinned = pinned,
                    updatedAt = now,
                    topicId = topicId,
                    triggerUserId = userId,
                )
                .value
        rowsAffected > 0L
      }

  override suspend fun archiveTopic(
      userId: UserId,
      topicId: TopicId,
      archived: Boolean,
  ): Boolean =
      withContext(dispatcher) {
        val rowsAffected =
            database.topicUpdateQueries
                .updateTopicArchived(
                    newArchived = archived,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                    topicId = topicId,
                    triggerUserId = userId,
                    adminRoleValue = TopicMemberRole.Admin,
                )
                .value
        rowsAffected > 0L
      }

  override suspend fun deleteTopic(userId: UserId, topicId: TopicId): Boolean =
      withContext(dispatcher) {
        val rowsAffected =
            database.topicQueries
                .deleteTopic(
                    topicId = topicId,
                    triggerUserId = userId,
                    adminRoleValue = TopicMemberRole.Admin,
                )
                .value
        rowsAffected > 0L
      }

  override suspend fun getMessageDraft(userId: UserId, topicId: TopicId): MessageDraft? =
      withContext(dispatcher) {
        val entity =
            database.topicSelectQueries
                .getMessageDraft(topicId = topicId, userId = userId)
                .executeAsOneOrNull() ?: return@withContext null
        buildMessageDraft(
            quotedMessageId = entity.draft_quoted_message_id,
            textContent = entity.draft_text_content,
            voiceContent = entity.draft_voice_content
        )
      }

  override suspend fun setMessageDraftQuotedMessageId(
      userId: UserId,
      topicId: TopicId,
      quotedMessageId: MessageId,
  ): Boolean =
      withContext(dispatcher) {
        database.topicUpdateQueries
            .updateTopicMessageDraftQuotedMessageId(
                quotedMessageId,
                topicId = topicId,
                triggerUserId = userId,
            )
            .value > 0L
      }

  override suspend fun setMessageDraftVoiceContent(
      userId: UserId,
      topicId: TopicId,
      draft: VoiceDraft?,
  ): Boolean =
      withContext(dispatcher) {
        val wrapperValue =
            when (draft) {
              null -> null
              is VoiceDraft -> Result.success(draft)
            }
        database.topicUpdateQueries
            .updateTopicMessageDraftVoice(
                wrapperValue,
                topicId = topicId,
                triggerUserId = userId,
            )
            .value > 0L
      }

  override suspend fun setMessageDraftTextContent(
      userId: UserId,
      topicId: TopicId,
      draft: String?,
  ): Boolean =
      withContext(dispatcher) {
        database.topicUpdateQueries
            .updateTopicMessageDraftText(
                newText = draft,
                topicId = topicId,
                triggerUserId = userId,
            )
            .value > 0L
      }

  override fun syncUpdateTopicLastReadMessageId(
      userId: UserId,
      topicId: TopicId,
      messageId: MessageId,
  ): Boolean =
      database.topicUpdateQueries
          .updateTopicLastReadMessageId(
              lastReadMessageId = messageId,
              topicId = topicId,
              triggerUserId = userId,
          )
          .value > 0L

  override suspend fun markTopicAsRead(userId: UserId, topicId: TopicId): Boolean =
      withContext(dispatcher) {
        database.topicUpdateQueries
            .markTopicAsRead(
                topicId = topicId,
                userId = userId,
            )
            .value > 0L
      }

  private fun TopicEntity.toDomain() =
      Topic(
          id = id,
          name = name,
          avatarModel = avatar_model,
          description = description,
          creatorId = creator_id,
          createdAt = Instant.fromEpochMilliseconds(created_at),
          archived = archived,
      )

  private fun TopicPreferenceEntity.toDomain() =
      TopicPreference(
          topicId = topic_id,
          userId = user_id,
          isPinned = pinned,
          background = background,
      )

  private fun HomeTopicEntity.toDomain(): HomeTopic {
    val lastMessagePreview =
        if (last_message_type != null && last_message_sender_id != null) {
          MessagePreview(
              type = last_message_type,
              text = last_message_text,
              senderId = last_message_sender_id,
              senderName = last_message_sender_name,
          )
        } else {
          null
        }
    val messageDraft = buildMessageDraft(
        quotedMessageId = draft_quoted_message_id,
        textContent = draft_text_content,
        voiceContent = draft_voice_content
    )
    return HomeTopic(
        id = id,
        name = name,
        avatarModel = avatar_model,
        description = description,
        isPinned = pinned == 1L,
        hasUnread = has_unread == 1L,
        messageUpdatedAt = Instant.fromEpochMilliseconds(topic_message_update_at),
        lastMessagePreview = lastMessagePreview,
        draft = messageDraft,
    )
  }

  private fun buildMessageDraft(
      quotedMessageId: MessageId?,
      textContent: String?,
      voiceContent: Result<VoiceDraft>?,
  ): MessageDraft? {
    return if (textContent.isNullOrEmpty() && voiceContent == null && quotedMessageId == null) {
      null
    } else {
      MessageDraft(
          inputMode = null,
          quotedMessageId = quotedMessageId,
          text = textContent,
          voiceNote = voiceContent?.getOrNull(),
      )
    }
  }
}
