package top.fseasy.imlog.features.selectstorage

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.InitializeUserStorageUseCase
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import javax.inject.Inject

sealed interface UriSelectState {
    data object IDLE : UriSelectState
    data object PROCESSING : UriSelectState
    data class Success(val rootDirName: String) : UriSelectState
    data class Failure(val cause: Throwable) : UriSelectState
}

@Immutable
data class SelectSharedStorageUiState(
    val selectState: UriSelectState = UriSelectState.IDLE,
)

class SelectSharedStorageViewModel @Inject constructor(
    private val initializeUserStorageUseCase: InitializeUserStorageUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<SelectSharedStorageUiState>(SelectSharedStorageUiState())
    val uiState = _uiState.asStateFlow()

    fun onSelectUriPermissionGrantSuccess(userId: UserId, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectState = UriSelectState.PROCESSING) }
            initializeUserStorageUseCase(
                userId = userId, selectedUriStr = uri.toUriStr()
            ).onFailure { e ->
                _uiState.update { it.copy(selectState = UriSelectState.Failure(e)) }
            }
                .onSuccess { result ->
                    _uiState.update { it.copy(selectState = UriSelectState.Success(result.rootDirName)) }
                }
        }
    }

    fun onSelectUriFailure(cause: Throwable) {
        _uiState.update {
            it.copy(selectState = UriSelectState.Failure(cause))
        }
    }
}