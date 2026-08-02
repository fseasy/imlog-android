package top.fseasy.imlog.features.home.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject

data class HomeContextUiState(
    val currentUserId: UserId,
    val showCreateDialog: Boolean = false,
    val selectedTopicId: TopicId? = null,
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    userRepository: UserRepository,
    private val topicRepository: TopicRepository,
) : ViewModel() {

  private val _showCreateDialog = MutableStateFlow(false)
  private val _selectedTopicId = MutableStateFlow<TopicId?>(null)
  val authStateFlow = userRepository.authState

  /** If null, which means it's invalid state(loading, not login) */
  @OptIn(ExperimentalCoroutinesApi::class)
  val contextUiStateFlow: StateFlow<HomeContextUiState?> =
      authStateFlow
          .filterIsInstance<AuthState.Authenticated>()
          .flatMapLatest { authState ->
            combine(
                _showCreateDialog,
                _selectedTopicId,
            ) { showDialog, selectedId ->
              HomeContextUiState(
                  currentUserId = authState.userId,
                  showCreateDialog = showDialog,
                  selectedTopicId = selectedId,
              )
            }
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
              initialValue = null,
          )

  fun showCreateDialog() {
    _showCreateDialog.value = true
  }

  fun hideCreateDialog() {
    _showCreateDialog.value = false
  }

  fun selectTopic(topicId: TopicId?) {
    _selectedTopicId.value = topicId
  }

  fun createTopic(name: String, description: String? = null) {
    launchWithUid { userId ->
      topicRepository.createNewTopic(
          userId,
          name = name,
          avatarModel = AvatarModel.TopicPreset(TopicPresetAvatar.random()),
          description = description,
      )
      _showCreateDialog.value = false
    }
  }

  private fun launchWithUid(block: suspend (UserId) -> Unit) {
    val uid = contextUiStateFlow.value?.currentUserId ?: return
    viewModelScope.launch { block(uid) }
  }
}
