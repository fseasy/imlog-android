package top.fseasy.imlog.features.auth

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
import top.fseasy.imlog.domain.usecase.SignInUpUseCase
import top.fseasy.imlog.ui.model.TaskExecuteState
import javax.inject.Inject

@Immutable
data class SignInUpCreateUserUiState(
    val createUserState: TaskExecuteState = TaskExecuteState.Idle,
)

@HiltViewModel
class SignInUpCreateUserViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val signInUpUseCase: SignInUpUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUpCreateUserUiState())
    val uiState = _uiState.asStateFlow()

    private var hasInitializedUserCreation = false;

    fun createUser() {
        // Guard duplicated creation! use this following sync-checking to avoid
        if (hasInitializedUserCreation) return
        hasInitializedUserCreation = true

        viewModelScope.launch {
            _uiState.update { it.copy(createUserState = TaskExecuteState.Executing) }
            runCatching {
                signInUpUseCase.createNewUserBySamplingProfile()
            }.onFailure { e ->
                Timber.e(e, "Create User failed, msg = [${e.message}]")
                _uiState.update {
                    it.copy(
                        createUserState = TaskExecuteState.Failure(
                            e.message ?: context.getString(R.string.error_unknown)
                        )
                    )
                }
            }
                .onSuccess {
                    _uiState.update { it.copy(createUserState = TaskExecuteState.Success) }
                }
        }
    }
}