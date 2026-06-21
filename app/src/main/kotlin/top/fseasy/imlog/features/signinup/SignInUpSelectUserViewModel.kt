package top.fseasy.imlog.features.signinup

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
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
import top.fseasy.imlog.ui.model.TaskExecuteState
import javax.inject.Inject

@Immutable
data class SignInUpSelectUserUiState(
    val selectUserState: TaskExecuteState = TaskExecuteState.Idle,
    val selectedUserId: UserId? = null, // mainly for UI rendering
)

@HiltViewModel
class SignInUpSelectUserViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val signInUpUseCase: SignInUpUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUpSelectUserUiState())
    val uiState = _uiState.asStateFlow()

    fun selectUser(userId: UserId) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectUserState = TaskExecuteState.Executing, selectedUserId = userId
                )
            } // make UI change!
            runCatching {
                signInUpUseCase.setCurrentUser(userId)
            }.fold(
                onSuccess = { _uiState.update { it.copy(selectUserState = TaskExecuteState.Success) } },
                onFailure = { e ->
                    Timber.e(e, "Select User failed")
                    _uiState.update {
                        it.copy(
                            selectUserState = TaskExecuteState.Failure(
                                e.message ?: context.getString(R.string.error_unknown)
                            )
                        )
                    }
                })
        }
    }

    fun onErrorDismiss() {
        // reset state
        _uiState.update { it.copy(selectUserState = TaskExecuteState.Idle, selectedUserId = null) }
    }
}