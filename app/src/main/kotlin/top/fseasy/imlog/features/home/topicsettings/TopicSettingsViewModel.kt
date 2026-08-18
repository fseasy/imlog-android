package top.fseasy.imlog.features.home.topicsettings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPreference
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.navigation.MainScreen
import javax.inject.Inject

sealed interface TopicSettingsUiState {
  data object Loading : TopicSettingsUiState

  data class Error(val userFriendlyReason: String) : TopicSettingsUiState

  data class Success(val userId: UserId, val topic: Topic, val preference: TopicPreference) :
      TopicSettingsUiState
}

@HiltViewModel
class TopicSettingsViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val topicRepository: TopicRepository,
    private val userRepository: UserRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

  val topicId = TopicId(savedStateHandle.toRoute<MainScreen.TopicSettings>().topicId)

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiStateFlow: StateFlow<TopicSettingsUiState> =
      userRepository.authState
          .filterIsInstance<AuthState.Authenticated>()
          .flatMapLatest { authState ->
            combine(
                topicRepository.observeTopicOrNull(topicId),
                topicRepository.observeTopicPreferenceOrNull(
                    userId = authState.userId,
                    topicId = topicId,
                ),
            ) { topic, preference ->
              if (topic == null || preference == null) {
                TopicSettingsUiState.Error(
                    context.getString(R.string.topic_settings_error_load_data_failure)
                )
              } else {
                TopicSettingsUiState.Success(
                    userId = preference.userId,
                    topic = topic,
                    preference = preference,
                )
              }
            }
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
              initialValue = TopicSettingsUiState.Loading,
          )

  fun updateTopicName(name: String) {
    launchWithSuccessUiState { state ->
      topicRepository.updateTopicName(
          userId = state.userId,
          topicId = topicId,
          newName = name,
      )
    }
  }

  fun togglePin() {
    launchWithSuccessUiState { state ->
      topicRepository.pinTopic(
          userId = state.userId,
          topicId = topicId,
          pinned = !state.preference.isPinned,
      )
    }
  }

  fun toggleArchive() {
    launchWithSuccessUiState { state ->
      topicRepository.archiveTopic(
          userId = state.userId,
          topicId = topicId,
          archived = !state.preference.isArchived,
      )
    }
  }

  fun deleteTopic() {
    launchWithSuccessUiState { state ->
      topicRepository.deleteTopic(userId = state.userId, topicId = topicId)
    }
  }

  private fun launchWithSuccessUiState(block: suspend (TopicSettingsUiState.Success) -> Unit) {
    val uiState = uiStateFlow.value
    if (uiState !is TopicSettingsUiState.Success) {
      return
    }
    viewModelScope.launch { block(uiState) }
  }
}
