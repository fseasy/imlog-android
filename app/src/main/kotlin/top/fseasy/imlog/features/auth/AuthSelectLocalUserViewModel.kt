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
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.SignInUpUseCase
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.ui.model.TaskExecuteState
import top.fseasy.imlog.ui.model.UserAvatarUiModel
import top.fseasy.imlog.ui.model.buildUserAvatarNioPath
import top.fseasy.imlog.ui.model.toUiModel
import javax.inject.Inject

data class LocalUser(val id: UserId, val name: String, val avatar: UserAvatarUiModel)

@Immutable
data class AuthSelectLocalUserUiState(
    val loadLocalUserState: TaskExecuteState<List<LocalUser>> = TaskExecuteState.Idle,
    val selectedUserId: UserId? = null, // mainly for UI rendering
    val selectUserState: TaskExecuteState<Unit> = TaskExecuteState.Idle,
)

@HiltViewModel
class AuthSelectLocalUserViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val signInUpUseCase: SignInUpUseCase,
    private val storagePathUseCase: StoragePathUseCase,
) : ViewModel() {
  private val _uiState = MutableStateFlow(AuthSelectLocalUserUiState())
  val uiState = _uiState.asStateFlow()

  init {
    loadUsers()
  }

  fun selectUser(userId: UserId) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
            selectUserState = TaskExecuteState.Executing,
            selectedUserId = userId,
        )
      } // make UI change!
      runCatching {
            signInUpUseCase.setCurrentUser(userId)
          }
          .fold(
              onSuccess = {
                _uiState.update {
                  it.copy(selectUserState = TaskExecuteState.Success(Unit))
                }
              },
              onFailure = { e ->
                Timber.e(e, "Select User failed")
                _uiState.update {
                  it.copy(
                      selectUserState =
                          TaskExecuteState.Failure(
                              e.message ?: context.getString(R.string.error_unknown)
                          )
                  )
                }
              },
          )
    }
  }

  fun onErrorDismiss() {
    // reset state
    _uiState.update { it.copy(selectUserState = TaskExecuteState.Idle, selectedUserId = null) }
  }

  private fun loadUsers() {
    viewModelScope.launch {
      _uiState.update { it.copy(loadLocalUserState = TaskExecuteState.Executing) }
      runCatching {
            signInUpUseCase.getLocalSignedInUsers()
          }
          .fold(
              onSuccess = { users ->
                val localUsers = users.map { u ->
                  LocalUser(
                      id = u.id,
                      name = u.username,
                      avatar =
                          u.avatarModel.toUiModel { filename ->
                            buildUserAvatarNioPath(
                                signInUserId = u.id,
                                storagePathUseCase = storagePathUseCase,
                                context = context,
                                filename = filename,
                            )
                          },
                  )
                }
                _uiState.update {
                  it.copy(loadLocalUserState = TaskExecuteState.Success(localUsers))
                }
              },
              onFailure = { e ->
                Timber.e(e, "Get local SignedIn users failed")
                _uiState.update {
                  it.copy(
                      loadLocalUserState =
                          TaskExecuteState.Failure(
                              e.message ?: context.getString(R.string.error_unknown)
                          ),
                  )
                }
              },
          )
    }
  }
}
