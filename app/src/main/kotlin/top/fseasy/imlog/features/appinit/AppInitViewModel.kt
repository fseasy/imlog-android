package top.fseasy.imlog.features.appinit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.repository.AppInitRepository
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject


@HiltViewModel
class AppInitViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val appInitRepository: AppInitRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val initStep: StateFlow<AppInitStep> = userRepository.observeUserIdOrNull.flatMapLatest { uid ->
        when (uid) {
            null -> flowOf(AppInitStep.SignInUp)
            else ->
                appInitRepository.observeAppInitDataOrNull(uid)
                    .map { initData -> determineInitStep(initData) }
                    .catch { e ->
                        Timber.e(e, "Observer initStep got exception")
                        emit(AppInitStep.SignInUp)
                    }
        }
    }

        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly, // It will keep running while app running
            initialValue = AppInitStep.Loading
        )

    private fun determineInitStep(initData: AppInitData?): AppInitStep = when {
        initData == null -> AppInitStep.SignInUp
        !initData.storageUriSelected -> AppInitStep.SelectMediaStorageUri
        !initData.firstTopicCreated -> AppInitStep.CreateFirstTopic
        !initData.WelcomeShown -> AppInitStep.Welcome
        else -> AppInitStep.Finished
    }
}