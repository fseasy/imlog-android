package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringResId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.util.retryOnAnyException
import javax.inject.Inject

sealed interface CreateDefaultTopicResult {
    data object Success : CreateDefaultTopicResult
    data object SkipCreate : CreateDefaultTopicResult
    data class Failure(val cause: Throwable) : CreateDefaultTopicResult
}

class CreateDefaultTopicUseCase @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val topicRepository: TopicRepository,
) {
    suspend operator fun invoke(
        userId: UserId,
    ) = runCatching {
        if (!shouldReallyCreateDefaultTopic(userId)) {
            return@runCatching CreateDefaultTopicResult.SkipCreate
        }
        val defaultName = resourceProvider.getString(StringResId.TopicInitialName)

    }

    private suspend fun shouldReallyCreateDefaultTopic(userId: UserId): Boolean {
        val topicNum = runCatching {
            retryOnAnyException {
                topicRepository.countAllRelatedTopicsForUser(userId)
            }
        }.getOrNull() ?: 0L
        return topicNum == 0L
    }

    private suspend fun InsertDefaultTopic(userId: UserId, topicName: String) {

    }
}