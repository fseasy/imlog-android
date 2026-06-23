package top.fseasy.imlog.features.appinit

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.ui.util.toDisplayMessage
import javax.inject.Inject


@Immutable
data class AppInitDispatchUiState(
    val initStep: AppInitStep? = null,
    val errorDisplayMessage: String? = null,
)

@HiltViewModel
class AppInitDispatchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppInitDispatchUiState())
    val uiState = _uiState.asStateFlow()

    init {
        checkInitStep()
    }

    private fun checkInitStep() {
        viewModelScope.launch {
            runCatching {
                val userId = userRepository.observeCurrentUserIdOrNull()
                    .firstOrNull()
                when (userId) {
                    null -> updateUiState(step = AppInitStep.Auth, error = null)

                    else -> {
                        val initData = userRepository.observeUserAppInitDataOrNull(userId)
                            .firstOrNull()
                        val step = determineInitStep(initData)
                        updateUiState(step = step, error = null)
                    }
                }
            }.onFailure { e -> updateUiState(step = null, error = e) }
        }
    }

    private fun determineInitStep(initData: AppInitData?): AppInitStep = when {
        initData == null -> AppInitStep.Auth
        !initData.storageUriSelected -> AppInitStep.SelectMediaStorageUri(initData.userId)
        !initData.WelcomeShown -> AppInitStep.Welcome(
            userId = initData.userId, needCreateFirstTopic = !initData.firstTopicCreated
        )

        else -> AppInitStep.Finished
    }

    private fun updateUiState(step: AppInitStep?, error: Throwable?) {
        // Make them exclusive
        if (error != null) {
            _uiState.update {
                it.copy(
                    errorDisplayMessage = error.toDisplayMessage(context), initStep = null
                )
            }
        } else {
            requireNotNull(step) { "Error is Null while step is also null" }
            _uiState.update { it.copy(initStep = step, errorDisplayMessage = null) }
        }
    }
}