package top.fseasy.imlog.features.appinit

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
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
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.ui.util.toDisplayMessage

@Immutable
data class AppInitDispatchUiState(
    val initStep: AppInitStep? = null,
    val errorDisplayMessage: String? = null,
)

@HiltViewModel
class AppInitDispatchViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
) : ViewModel() {

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<AppInitDispatchUiState> =
      userRepository.authState
          .flatMapLatest { auth ->
            when (auth) {
              AuthState.Loading -> flowOf(AppInitDispatchUiState())
              AuthState.Unauthenticated -> {
                Timber.i("UnAuth")
                flowOf(
                    AppInitDispatchUiState(
                        initStep = AppInitStep.Auth,
                        errorDisplayMessage = null,
                    )
                )
              }

              is AuthState.Authenticated -> {
                Timber.i("Auth")
                userRepository.observeUserAppInitDataOrNull(auth.userId).map { initData ->
                  val step = determineInitStep(initData)
                  Timber.i("step = %s", step)
                  AppInitDispatchUiState(initStep = step, errorDisplayMessage = null)
                }
              }
            }
          }
          .catch { e ->
            emit(
                AppInitDispatchUiState(
                    initStep = null,
                    errorDisplayMessage = e.toDisplayMessage(context),
                )
            )
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5_000),
              initialValue = AppInitDispatchUiState(),
          )

  private fun determineInitStep(initData: AppInitData?): AppInitStep =
      when {
        initData == null -> AppInitStep.Auth
        !initData.storageUriSelected -> {
          Timber.i("Select media storage uri, userid=%s", initData.userId)
          AppInitStep.SelectMediaStorageUri(initData.userId)
        }
        !initData.welcomeShown ->
            AppInitStep.Welcome(
                userId = initData.userId,
                needCreateFirstTopic = !initData.firstTopicCreated,
            )

        else -> AppInitStep.Finished
      }
}
