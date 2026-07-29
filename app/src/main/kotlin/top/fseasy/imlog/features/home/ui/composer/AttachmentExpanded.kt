package top.fseasy.imlog.features.home.ui.composer

import android.net.Uri
import android.provider.MediaStore.getPickImagesMaxLimit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R

import top.fseasy.imlog.features.home.model.MessageInputModeParcelable
import top.fseasy.imlog.ui.components.AppIconButton

@Composable
fun AttachmentExpanded(
    inputMode: MessageInputModeParcelable?,
    onSelectAlbums: (uris: List<Uri>) -> Unit,
    onSelectAudios: (uris: List<Uri>) -> Unit,
    onSelectFiles: (uris: List<Uri>) -> Unit,
    closeExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    if (inputMode != MessageInputModeParcelable.Attachment) return

    val focusRequester = remember { FocusRequester() }

    SideEffect {
        focusRequester.requestFocus()
    }
    val albumPickerLauncher = rememberLauncherForActivityResult(
        // getPickImagesMaxLimit() Needs R Extensions Version 2. so just set a hard limit
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onSelectAlbums(uris)
                closeExpanded()
            }
        }
    )
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onSelectAudios(uris)
                closeExpanded()
            }
        }
    )
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onSelectFiles(uris)
                closeExpanded()
            }
        }
    )

    Surface(tonalElevation = 8.dp) {
        Row(
            modifier = modifier
                .height(height)
                .wrapContentHeight()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentSelectorButton(
                onClick = {
                    albumPickerLauncher.launch(
                        PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)
                    )
                },
                icon = ImageVector.vectorResource(R.drawable.icon_image),
                description = stringResource(R.string.composer_attachment_icon_album),
                modifier = modifier
            )
            AttachmentSelectorButton(
                onClick = {
                    audioPickerLauncher.launch(arrayOf("audio/*"))
                },
                icon = ImageVector.vectorResource(R.drawable.icon_audio_file),
                description = stringResource(R.string.composer_attachment_icon_audio_file),
                modifier = modifier
            )
            AttachmentSelectorButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                icon = ImageVector.vectorResource(R.drawable.icon_files),
                description = stringResource(R.string.composer_attachment_icon_files),
                modifier = modifier
            )
        }
    }
}


@Composable
private fun AttachmentSelectorButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
) {
    val backgroundModifier = Modifier
    val tint = LocalContentColor.current
    AppIconButton(
        onClick = onClick,
        buttonModifier = modifier.then(backgroundModifier),
        icon = icon,
        contentDescription = description,
        iconModifier = Modifier
            .size(56.dp)
            .padding(8.dp),
        tint = tint
    )
}
