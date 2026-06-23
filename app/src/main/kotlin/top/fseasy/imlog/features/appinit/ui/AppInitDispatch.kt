package top.fseasy.imlog.features.appinit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.features.appinit.AppInitDispatchViewModel
import top.fseasy.imlog.features.appinit.AppInitStep


@Composable
fun AppInitDispatch(
    onStepNavigate: (AppInitStep) -> Unit,
    viewModel: AppInitDispatchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.initStep) {
        when (val step = uiState.initStep) {
            null -> Unit
            AppInitStep.Auth -> onStepNavigate(step)
            is AppInitStep.SelectMediaStorageUri -> onStepNavigate(step)
            is AppInitStep.Welcome -> onStepNavigate(step)
            AppInitStep.Finished -> onStepNavigate(step)
        }
    }
    // NO UI for this
}