package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Composer2Container(
    onNavigateBack: () -> Unit,
    onShowSnackbar: (messageResId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: Composer2ViewModel = hiltViewModel(),
    onBaseHeightPxChanged: (Int) -> Unit = {},
) {

}
