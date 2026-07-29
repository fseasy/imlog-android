package top.fseasy.imlog.features.home.ui.composer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.features.home.MessageComposerViewModel
import top.fseasy.imlog.features.home.model.ComposerDraftMeta
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

@Composable
fun MessageComposer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessageComposerViewModel = hiltViewModel(),
) {
    val draftMeta by viewModel.draftMetaUiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    val isKeyBoardVisible = WindowInsets.ime.getBottom(density) > 0

    BackHandler(true) { viewModel.handleBackPress(isKeyBoardVisible) }

    Surface(tonalElevation = 2.dp, contentColor = MaterialTheme.colorScheme.secondary) {
        Column(modifier = modifier) {
            // TODO: QuoteMessage Panel
            UserInputRow((draftMeta as? ComposerDraftMeta.Ready)?.inputMode, modifier = modifier)
            AttachmentExpanded(
                inputMode = TODO(),
                onSelectAlbums = TODO(),
                onSelectAudios = TODO(),
                onSelectFiles = TODO(),
                closeExpanded = TODO(),
                modifier = TODO(),
                height = TODO()
            )
        }
    }
}