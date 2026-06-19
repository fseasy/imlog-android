package top.fseasy.imlog.features.appinit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.features.appinit.AppInitStep
import top.fseasy.imlog.features.appinit.AppInitViewModel
import top.fseasy.imlog.features.selectstorage.SharedStorageSelectScreen
import top.fseasy.imlog.features.signinup.ui.SignInUpNavigation
import top.fseasy.imlog.ui.components.InternalErrorInfoText

@Composable
fun appInitGate(appInitViewModel: AppInitViewModel = hiltViewModel()) {
    val uiState by appInitViewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.error != null) {
        InternalErrorInfoText(uiState.error?.message ?: "Unknown Error")
    } else {
        when (val step = uiState.initStep) {
            AppInitStep.Loading -> Splash()
            AppInitStep.SignInUp -> SignInUpNavigation()
            is AppInitStep.SelectMediaStorageUri -> SharedStorageSelectScreen(
                currentUserId = step.userId,
            )

            is AppInitStep.Welcome -> {
                val needCreateTopic = step.needCreateFirstTopic
                if (needCreateTopic) {
                    appInitViewModel.createFirstTopic(step.userId)
                }
                WelcomeScreen(isCreatingFirstTopic = needCreateTopic)
            }

            AppInitStep.Finished -> TODO()
        }
    }
}