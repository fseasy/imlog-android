package top.fseasy.imlog.features.signinup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.ui.components.AvatarUiModel
import top.fseasy.imlog.ui.components.toUIModel
import javax.inject.Inject

data class LocalUser(val id: UserId, val name: String, val avatar: AvatarUiModel)

data class SampledUserProfile(val name: String, val avatar: AvatarUiModel)

data class CreateUserState(
    val isLoading: Boolean = true,
    val sampledUser: SampledUserProfile? = null,
    val sampleError: String? = null,
    val createError: String? = null,
)

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
            _uiState.value = SignInUpUiState(isLoading = false, users = localUsers, error = error)
        }
    }

    suspend fun createUser(sampledUser: SampledUserProfile) {
        viewModelScope.launch {

        }
    }
}