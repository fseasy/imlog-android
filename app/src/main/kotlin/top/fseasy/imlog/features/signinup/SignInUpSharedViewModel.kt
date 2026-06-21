package top.fseasy.imlog.features.signinup

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.SignInUpUseCase
import top.fseasy.imlog.ui.model.UserAvatarUiModel
import top.fseasy.imlog.ui.model.toUserAvatarUIModel
import javax.inject.Inject

data class LocalUser(val id: UserId, val name: String, val avatar: UserAvatarUiModel)


@Immutable
data class SignInUpHostUiState(
    val isLoading: Boolean = true,
    val users: List<LocalUser> = emptyList(),
    val loadingErrorMessage: String? = null,
)

@HiltViewModel
class SignInUpSharedViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val signInUpUseCase: SignInUpUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUpHostUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            runCatching {
                signInUpUseCase.getLocalSignedInUsers()
            }.onSuccess { users ->
                val localUsers = users.map { u ->
                    LocalUser(
                        id = u.id, name = u.username, avatar = u.avatarModel.toUserAvatarUIModel()
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false, users = localUsers
                    )
                }
            }
                .onFailure { e ->
                    Timber.e(e, "Get local SignedIn users failed")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingErrorMessage = e.message
                                ?: context.getString(R.string.error_unknown)
                        )
                    }
                }
        }
    }


}