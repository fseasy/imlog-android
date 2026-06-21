package top.fseasy.imlog.features.appinit

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
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.CreateFirstTopicResult
import top.fseasy.imlog.domain.usecase.WelcomeUseCase
import top.fseasy.imlog.ui.model.TaskExecuteState
import javax.inject.Inject

@Immutable
data class WelcomeUiState(
    val topicCreateState: TaskExecuteState = TaskExecuteState.Idle,
    val markWelcomeState: TaskExecuteState = TaskExecuteState.Idle,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val welcomeUseCase: WelcomeUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WelcomeUiState>(WelcomeUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * The decision of whether to create topic is passed from the screen.
     * To guard the creation action, use this var.
     * */
    private var hasInitializedFirstTopicCreation = false

    suspend fun createFirstTopic(userId: UserId) {
        if (hasInitializedFirstTopicCreation) return
        hasInitializedFirstTopicCreation = true
        viewModelScope.launch {
            _uiState.update { it.copy(topicCreateState = TaskExecuteState.Executing) }
            when (val r = welcomeUseCase.createDefaultTopic(userId)) {
                is CreateFirstTopicResult.SkipCreate,
                is CreateFirstTopicResult.Success,
                    -> _uiState.update { it.copy(topicCreateState = TaskExecuteState.Success) }

                is CreateFirstTopicResult.Failure -> _uiState.update {
                    it.copy(
                        topicCreateState = TaskExecuteState.Failure(
                            r.cause.message ?: context.getString(R.string.error_unknown)
                        )
                    )
                }
            }
        }
    }

    suspend fun markWelcomeDone(userId: UserId) {
        viewModelScope.launch {
            _uiState.update {

            }
        }
    }
}