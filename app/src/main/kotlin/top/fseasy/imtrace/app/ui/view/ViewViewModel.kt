package top.fseasy.imtrace.app.ui.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.fseasy.imtrace.app.data.repository.MessageRepository
import top.fseasy.imtrace.app.domain.model.Statistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ViewUiState(
    val isLoading: Boolean = true,
    val statistics: Statistics = Statistics(0, 0)
)

@HiltViewModel
class ViewViewModel @Inject constructor(
    messageRepository: MessageRepository
) : ViewModel() {

    val uiState: StateFlow<ViewUiState> = messageRepository.getStatistics()
        .map { stats ->
            ViewUiState(
                isLoading = false,
                statistics = stats
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewUiState()
        )
}
