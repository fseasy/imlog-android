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
    val topicCreateState: TaskExecuteState<Unit> = TaskExecuteState.Idle,
    val markWelcomeState: TaskExecuteState<Unit> = TaskExecuteState.Idle,
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
    private var hasInitializedAutoFirstTopicCreation = false

    /**
     * Use this with creating guarding.
     */
    fun autoCreateFirstTopic(userId: UserId) {
        if (hasInitializedAutoFirstTopicCreation) return
        hasInitializedAutoFirstTopicCreation = true
        triggerCreateFirstTopic(userId)
    }

    /**
     * Use this with explicit indent.
     */
    fun triggerCreateFirstTopic(userId: UserId) {
        viewModelScope.launch {
            _uiState.update { it.copy(topicCreateState = TaskExecuteState.Executing) }
            when (val r = welcomeUseCase.createFirstTopicWithDefaultValueAndMarkInit(userId)) {
                is CreateFirstTopicResult.SkipCreate,
                is CreateFirstTopicResult.Success,
                    -> _uiState.update { it.copy(topicCreateState = TaskExecuteState.Success(Unit)) }

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

    fun markWelcomeShown(userId: UserId) {
        viewModelScope.launch {
            _uiState.update { it.copy(markWelcomeState = TaskExecuteState.Executing) }
            welcomeUseCase.markWelcomeShown(userId)
                .fold(onSuccess = {
                    _uiState.update {
                        it.copy(
                            markWelcomeState = TaskExecuteState.Success(
                                Unit
                            )
                        )
                    }
                }, onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            markWelcomeState = TaskExecuteState.Failure(
                                e.message ?: context.getString(R.string.error_unknown)
                            )
                        )
                    }
                })
        }
    }
}