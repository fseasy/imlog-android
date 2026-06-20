package top.fseasy.imlog.features.signinup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.AppStateRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.SampleUserProfileUseCase
import top.fseasy.imlog.ui.components.AvatarUiModel
import top.fseasy.imlog.ui.components.toUIModel
import top.fseasy.imlog.data.util.retryOnAnyException
import javax.inject.Inject

data class LocalUser(val id: UserId, val name: String, val avatar: AvatarUiModel)

sealed interface CreateUserState {
    data object Running : CreateUserState
    data object Success : CreateUserState

    @Immutable
    data class Failure(val message: String) : CreateUserState
}

@Immutable
data class SignInUpUiState(
    val isLoading: Boolean = true,
    val users: List<LocalUser> = emptyList(),
    val createUserState: CreateUserState? = null,
    val error: String? = null,
)

@HiltViewModel
class SignInUpViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val appStateRepository: AppStateRepository,
    private val sampleUserUseCase: SampleUserProfileUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUpUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            val (users, error) = try {
                val users = userRepository.getLocalSignedInUsers()
                users to null
            } catch (e: Exception) {
                Timber.e(e, "Get local SignedIn users failed")
                emptyList<User>() to e.toString()
            }
            val localUsers = users.map { u ->
                LocalUser(
                    id = u.id, name = u.username, avatar = u.avatarModel.toUIModel()
                )
            }
            _uiState.update { it.copy(isLoading = false, users = localUsers, error = error) }
        }
    }

    private var hasInitializedUserCreation = false;

    fun createUser() {
        // Guard duplicated creation! use this following sync-checking to avoid
        if (hasInitializedUserCreation) return
        hasInitializedUserCreation = true

        viewModelScope.launch {
            _uiState.update { it.copy(createUserState = CreateUserState.Running) }
            val username = sampleUserUseCase.sampleUsername()
            val avatar = sampleUserUseCase.samplePresetAvatar()
            runCatching {
                retryOnAnyException {
                    userRepository.createAndSetCurrentUserOrThrow(
                        username = username, avatarModel = avatar
                    )
                }
            }.onFailure { e ->
                Timber.e(e, "Create User failed, msg = [${e.message}]")
                _uiState.update {
                    it.copy(
                        createUserState = CreateUserState.Failure(e.message ?: "unknown error")
                    )
                }
            }
                .onSuccess {
                    _uiState.update { it.copy(createUserState = CreateUserState.Success) }

                }

        }
    }

    fun selectUser(user: LocalUser) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) } // make UI change!
            appStateRepository.setCurrentId(user.id)
        }
    }
}