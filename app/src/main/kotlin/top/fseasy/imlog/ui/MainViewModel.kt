package top.fseasy.imlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.fseasy.imlog.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isLoading: Boolean = true,
    val needsOnboarding: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.getCurrentUser().collect { user ->
                _uiState.value = MainUiState(
                    isLoading = false,
                    needsOnboarding = user == null
                )
            }
        }
    }

    fun createUser(username: String) {
        viewModelScope.launch {
            userRepository.createCurrentUser(username)
        }
    }
}
