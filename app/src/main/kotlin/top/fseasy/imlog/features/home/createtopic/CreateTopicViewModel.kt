package top.fseasy.imlog.features.home.createtopic

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.data.mapper.toAppPath
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.data.util.TempFileCleaner
import top.fseasy.imlog.di.ApplicationIoScope
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AppImageFormat
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.util.runSuspendCatching
import top.fseasy.imlog.ui.model.AvatarUiModel
import top.fseasy.imlog.ui.model.TaskExecuteWithDefaultState
import top.fseasy.imlog.ui.model.TopicAvatarUiModel
import top.fseasy.imlog.ui.model.random
import top.fseasy.imlog.ui.model.toDomain
import javax.inject.Inject
import kotlin.io.path.name
import kotlin.time.Clock

@Immutable
data class CreateTopicUiState(
    val isCreatingTopic: Boolean = false,
    val selectAvatarTask: TaskExecuteWithDefaultState<TopicAvatarUiModel> =
        TaskExecuteWithDefaultState.Idle(AvatarUiModel.Preset.random()),
)

sealed interface CreateTopicEffect {
  data class NavigateToTopic(val topicId: TopicId) : CreateTopicEffect

  data class ShowSnackBar(val message: String) : CreateTopicEffect
}

@HiltViewModel
class CreateTopicViewModel
@Inject
constructor(
    userRepository: UserRepository,
    private val topicRepository: TopicRepository,
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    @param:ApplicationContext private val context: Context,
    @ApplicationIoScope applicationIoScope: CoroutineScope,
) : ViewModel() {
  private val _tempFileCleaner = TempFileCleaner(applicationIoScope).also(::addCloseable)

  private val _uiStateFlow: MutableStateFlow<CreateTopicUiState> =
      MutableStateFlow(value = CreateTopicUiState())
  private val _authStateFlow = userRepository.authState
  private val _effect = Channel<CreateTopicEffect>()

  val uiStateFlow = _uiStateFlow.asStateFlow()
  val avatarOutputFormat = AppImageFormat.Webp
  val effect = _effect.receiveAsFlow()

  fun createTopic(name: String, description: String?, avatarUiModel: TopicAvatarUiModel) {
    _uiStateFlow.update { it.copy(isCreatingTopic = true) }
    launchWithUid { userId ->
      // Move avatar to follow pathUseCase, if it's file
      val savedAvatarUiModel =
          when (avatarUiModel) {
            is AvatarUiModel.Preset<TopicPresetAvatar> -> avatarUiModel
            is AvatarUiModel.NioPath -> {
              // Move the temp file to the persistent path with the StoragePathUseCase
              // Move = Copy + delete (it's dural write)
              val tempPath = avatarUiModel.path
              val targetPath =
                  storagePathUseCase.buildTopicAvatarStoragePath(userId, filename = tempPath.name)
              val copyResult =
                  storageRepository.copyFile(
                      AbsolutePathModel.AppPathModel(tempPath.toAppPath()),
                      targetPath = targetPath,
                      srcMimeType = avatarOutputFormat.mimeType,
                  )
              if (copyResult is FileCopyResult.Error) {
                _uiStateFlow.update { it.copy(isCreatingTopic = false) }
                _effect.send(
                    CreateTopicEffect.ShowSnackBar(
                        context.getString(
                            R.string.create_topic_err_text_failed_due_to_copy_avatar_error
                        )
                    )
                )
                Timber.w(copyResult.cause, "Copy Topic Avatar failed")
                return@launchWithUid
              }
              _tempFileCleaner.delete(tempPath)
              AvatarUiModel.NioPath(targetPath.toInternalOnly().toNioPath(context))
            }
          }
      runSuspendCatching {
            topicRepository.createNewTopic(
              userId,
              name = name,
              avatarModel = savedAvatarUiModel.toDomain(),
              description = description,
            )
          }
          .fold(
              onSuccess = { topicId -> _effect.send(CreateTopicEffect.NavigateToTopic(topicId)) },
              onFailure = { e ->
                Timber.w(e, "Create Topic failed due to db error")
                _effect.send(
                    CreateTopicEffect.ShowSnackBar(
                        context.getString(R.string.create_topic_err_text_failed_due_to_db_error)
                    )
                )
              },
          )
    }
    _uiStateFlow.update { it.copy(isCreatingTopic = false) }
  }

  fun selectPresetAvatar(avatarUiModel: AvatarUiModel.Preset<TopicPresetAvatar>) {
    _uiStateFlow.update {
      it.copy(selectAvatarTask = TaskExecuteWithDefaultState.Success(avatarUiModel))
    }
  }

  fun selectAvatarFromUri(croppedImageUri: Uri) {
    val previousAvatar = _uiStateFlow.value.selectAvatarTask.data
    _uiStateFlow.update {
      it.copy(selectAvatarTask = TaskExecuteWithDefaultState.Executing(previousAvatar))
    }
    launchWithUid { uid ->
      val filename =
          storagePathUseCase.buildTimestampedFilename(
              Clock.System.now().toEpochMilliseconds(),
              "topic_avatar_${uid.value}${avatarOutputFormat.filenameSuffix}",
          )
      val path =
          storagePathUseCase.buildTemporaryCacheFileStoragePath(userId = uid, filename = filename)

      val result =
          storageRepository.copyFile(
              AbsolutePathModel.UriStrModel(croppedImageUri.toUriStr()),
              targetPath = path,
              srcMimeType = avatarOutputFormat.mimeType,
          )
      when (result) {
        is FileCopyResult.Error -> {
          val reason = result.cause.message ?: context.getString(R.string.error_unknown)
          _uiStateFlow.update {
            it.copy(
                selectAvatarTask =
                    TaskExecuteWithDefaultState.Failure(reason = reason, data = previousAvatar)
            )
          }
        }

        is FileCopyResult.Success -> {
          val newModel = AvatarUiModel.NioPath(path.toNioPath(context))
          _uiStateFlow.update {
            it.copy(selectAvatarTask = TaskExecuteWithDefaultState.Success(newModel))
          }
          _tempFileCleaner.track(newModel.path)
        }
      }
    }
  }

  fun showSnackBarWhenCropAvatarFailed(reasonMessage: String) {
    viewModelScope.launch {
      val message = context.getString(R.string.create_topic_crop_image_failed_due_to, reasonMessage)
      _effect.send(CreateTopicEffect.ShowSnackBar(message))
    }
  }

  private fun launchWithUid(block: suspend (UserId) -> Unit) {
    val uid = (_authStateFlow.value as? AuthState.Authenticated)?.userId ?: return
    viewModelScope.launch { block(uid) }
  }
}
