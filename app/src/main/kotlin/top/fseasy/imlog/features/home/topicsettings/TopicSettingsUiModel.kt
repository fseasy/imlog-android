package top.fseasy.imlog.features.home.topicsettings

import android.content.Context
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPreference
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.ui.model.TopicAvatarUiModel
import top.fseasy.imlog.ui.model.buildTopicAvatarNioPath
import top.fseasy.imlog.ui.model.toUiModel

sealed interface TopicSettingsUiState {
  data object Loading : TopicSettingsUiState

  data class Error(val userFriendlyReason: String) : TopicSettingsUiState

  data class Success(
      val userId: UserId,
      val topic: SettingsTopicUiModel,
  ) : TopicSettingsUiState
}

data class SettingsTopicUiModel(
    val id: TopicId,
    val name: String,
    val description: String?,
    val avatarUiModel: TopicAvatarUiModel,
    val isPinned: Boolean,
    val isArchived: Boolean,
)

internal fun buildSettingsTopicUiModel(
    storagePathUseCase: StoragePathUseCase,
    context: Context,
    topic: Topic,
    preference: TopicPreference,
) =
    SettingsTopicUiModel(
        id = topic.id,
        name = topic.name,
        description = topic.description,
        avatarUiModel =
            topic.avatarModel.toUiModel { filename ->
              buildTopicAvatarNioPath(
                  signInUserId = preference.userId,
                  storagePathUseCase = storagePathUseCase,
                  context = context,
                  filename = filename,
              )
            },
        isArchived = preference.isArchived,
        isPinned = preference.isPinned,
    )
