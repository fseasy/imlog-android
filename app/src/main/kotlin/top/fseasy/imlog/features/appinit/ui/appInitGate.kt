package top.fseasy.imlog.features.appinit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.features.appinit.AppInitStep
import top.fseasy.imlog.features.appinit.AppInitViewModel

@Composable
fun appInitGate(appInitViewModel: AppInitViewModel = hiltViewModel()) {
    val initStep by appInitViewModel.initStep.collectAsStateWithLifecycle()

    when(initStep) {
        AppInitStep.Loading -> Splash()
        AppInitStep.SignInUp -> TODO()
        AppInitStep.SelectMediaStorageUri -> TODO()
        AppInitStep.CreateFirstTopic -> TODO()
        AppInitStep.Welcome -> TODO()
        AppInitStep.Finished -> TODO()
    }
}