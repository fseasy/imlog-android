package top.fseasy.imlog.features.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.model.Statistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject

data class ViewUiState(
    val isLoading: Boolean = true,
    val statistics: Statistics = Statistics(0, 0),
)

@HiltViewModel
class ViewViewModel @Inject constructor(
    messageRepository: MessageRepository,
    userRepository: UserRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ViewUiState> = userRepository.observeUserIdOrNull.filterNotNull()
        .flatMapLatest { uid ->
            messageRepository.observeStatistics(uid)
                .map { stats ->
                    ViewUiState(
                        isLoading = false,
                        statistics = stats
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewUiState()
        )
}
