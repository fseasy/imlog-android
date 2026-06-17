package top.fseasy.imlog.features.appinit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject

@Immutable
data class AppInitUiState(
    val initStep: AppInitStep = AppInitStep.Loading,
    val userId: UserId? = null,
    val error: Throwable? = null,
)

@HiltViewModel
class AppInitViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AppInitUiState> = userRepository.observeCurrentUserIdOrNull()
        .flatMapLatest { uid ->
            when (uid) {
                null -> flowOf(AppInitUiState(initStep = AppInitStep.SignInUp))
                else -> userRepository.observeUserAppInitDataOrNull(uid)
                    .map { initData ->
                        AppInitUiState(
                            initStep = determineInitStep(initData),
                            userId = uid
                        )
                    }
            }
        }
        .catch { e ->
            Timber.e(e, "Observer initStep got exception")
            emit(AppInitUiState(error = e))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly, // It will keep running while app running
            initialValue = AppInitUiState()
        )

    private fun determineInitStep(initData: AppInitData?): AppInitStep = when {
        initData == null -> AppInitStep.SignInUp
        !initData.storageUriSelected -> AppInitStep.SelectMediaStorageUri
        !initData.firstTopicCreated -> AppInitStep.CreateFirstTopic
        !initData.WelcomeShown -> AppInitStep.Welcome
        else -> AppInitStep.Finished
    }
}