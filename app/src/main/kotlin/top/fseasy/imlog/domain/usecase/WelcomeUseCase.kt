package top.fseasy.imlog.domain.usecase

import timber.log.Timber
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringResId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.RetryModel
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.domain.repository.DbTransactionRunner
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject

sealed interface CreateFirstTopicResult {
    data object Success : CreateFirstTopicResult
    data object SkipCreate : CreateFirstTopicResult
    data class Failure(val cause: Throwable) : CreateFirstTopicResult
}

class WelcomeUseCase @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val topicRepository: TopicRepository,
    private val userRepository: UserRepository,
    private val transactionRunner: DbTransactionRunner,
) {
    suspend fun createFirstTopicWithDefaultValueAndMarkInit(
        userId: UserId,
    ): CreateFirstTopicResult {
        if (!shouldReallyCreateDefaultTopic(userId)) {
            return CreateFirstTopicResult.SkipCreate
        }
        val name = resourceProvider.getString(StringResId.TopicInitialName)
        val description = resourceProvider.getString(StringResId.TopicInitialDescription)
        val avatar = AvatarModel.TopicPreset(TopicPresetAvatar.random())
        return runCatching {
            // 1. create new topic 2. mark first topic created
            transactionRunner.runInTransaction(retry = RetryModel.OnAnyException) {
                topicRepository.syncCreateNewTopic(
                    creatorId = userId, name = name, avatarModel = avatar, description = description
                )
                userRepository.syncUpdateAppInitFirstTopicCreated(userId)
            }
        }.fold(onSuccess = { CreateFirstTopicResult.Success }, onFailure = { e ->
            Timber.e(e, "CreateDefaultTopic failed due to db error")
            CreateFirstTopicResult.Failure(e)
        })
    }

    suspend fun markWelcomeDone(userId: UserId) {

    }

    private suspend fun shouldReallyCreateDefaultTopic(userId: UserId): Boolean {
        val topicNum = runCatching {
            topicRepository.countAllRelatedTopicsForUser(userId)
        }.getOrNull() ?: 0L
        return topicNum == 0L
    }

}