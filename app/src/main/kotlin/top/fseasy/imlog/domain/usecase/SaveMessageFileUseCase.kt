package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.StorageRepository
import javax.inject.Inject

class SaveMessageFileUseCase @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
) {
    suspend fun saveAudio(
        srcUriStr: UriStr,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
    ) = runCatching {

        val rawFilename =
            storagePathUseCase.prependDayAndTimeToFilename(messageTimestampMs, originalFilename = ?)
        storagePathUseCase.buildMessageRawFileAbsolutePath(
            userId = userId,
            topicId = topicId,
            timestampMs = messageTimestampMs,
            filename = rawFilename,
        )
    }
}