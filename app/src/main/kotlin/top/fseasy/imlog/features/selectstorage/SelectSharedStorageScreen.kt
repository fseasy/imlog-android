package top.fseasy.imlog.features.selectstorage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.ui.components.AppIconButton
import top.fseasy.imlog.ui.components.AppOutlinedButton
import top.fseasy.imlog.ui.components.HighlightConfig
import top.fseasy.imlog.ui.components.HighlightedText
import top.fseasy.imlog.ui.components.InternalErrorInfoText
import top.fseasy.imlog.util.FindOrCreateFileUriResult
import top.fseasy.imlog.util.UriStorageUtil

@Composable
fun SharedStorageSelectScreen(
    currentUserId: UserId,
    viewModel: SelectSharedStorageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SharedStorageSelectContent(uiState) { uri ->
        try {
            // persist permission.
            // This logic should STAY in ui as it need the context while
            // viewModel shouldn't hold the context
            val takeFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            viewModel.onSelectUriPermissionGrantSuccess(currentUserId, uri)
        } catch (e: Exception) {
            Timber.e(e, "Select and take permission failed")
            viewModel.onSelectUriFailure(e)
        }
    }
}

@Composable
fun SharedStorageSelectContent(
    uiState: SelectSharedStorageUiState,
    onSelect: (uri: Uri) -> Unit,
) {
    // 1. register folder picker to launch system picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onSelect(uri)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HighlightedText(
            text = stringResource(R.string.select_storage_slogan),
            style = MaterialTheme.typography.titleMedium,
            highlight = HighlightConfig(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.select_storage_select_tip),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(32.dp))

        when (val state = uiState.selectState) {
            is UriSelectState.IDLE,
            is UriSelectState.PROCESSING,
                -> AppOutlinedButton(
                onClick = { folderPickerLauncher.launch(null) },
                text = stringResource(R.string.btn_choose_storage),
                loading = state is UriSelectState.PROCESSING
            )

            is UriSelectState.Success -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.icon_check_circle),
                        contentDescription = stringResource(R.string.select_storage_btn_content_success),
                        tint = MaterialTheme.colorScheme.primaryContainer,
                    )
                    Text(
                        state.rootDirName, style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    stringResource(R.string.select_storage_all_set),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            is UriSelectState.Failure -> {
                InternalErrorInfoText(state.cause.message ?: stringResource(R.string.error_unknown))
                AppOutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    text = stringResource(R.string.btn_retry),
                )
            }
        }
    }
}

