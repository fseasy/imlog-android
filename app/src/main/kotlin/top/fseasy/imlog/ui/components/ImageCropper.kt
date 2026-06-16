package top.fseasy.imlog.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import top.fseasy.imlog.R

@Composable
fun ImageCropper(
    imageUri: Uri,
    cropOptions: CropImageOptions,
    cropTrigger: Boolean,
    onCropComplete: (CropImageView.CropResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    // remember the last trigger state. to avoid duplicated trigger on composing
    var lastTriggeredState by remember { mutableStateOf(false) }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { context ->
        CropImageView(context).apply {
            setImageCropOptions(cropOptions)
        }
    }, update = { view ->
        // update the listener every time to apply the latest action!
        // because when parent composing, this callback may change (if it's lambda with closure)
        view.setOnCropImageCompleteListener { _, result ->
            onCropComplete(result)
        }
        if (view.tag != imageUri) {
            view.setImageUriAsync(imageUri)
            view.tag = imageUri
        }
        // only crop when trigger changes.
        // (avoid call multiple crop while parent composing on cropTrigger = true)
        if (cropTrigger && !lastTriggeredState) {
            view.croppedImageAsync()
        }
        lastTriggeredState = cropTrigger
    }, onRelease = { view ->
        view.setOnCropImageCompleteListener(null)
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropImageDialog(
    title: String,
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropSuccess: (Uri) -> Unit,
    onCropFailure: (String) -> Unit,
) {
    // 使用 rememberSaveable 防止屏幕旋转导致状态重置
    var triggerCrop by rememberSaveable { mutableStateOf(false) }

    val cropOptions = remember {
        CropImageOptions().apply {
            // 1:1 circle
            cropShape = CropImageView.CropShape.OVAL
            aspectRatioX = 1
            aspectRatioY = 1
            fixAspectRatio = true
        }
    }

    Dialog(
        onDismissRequest = {
            // disable close Dialog while cropping
            if (!triggerCrop) {
                onDismiss()
            }
        }, properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // also disable back-key / outside-click dismiss while cropping
            dismissOnBackPress = !triggerCrop, dismissOnClickOutside = !triggerCrop
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        AppBackIconButton(onClick = onDismiss, enabled = !triggerCrop)
                    })
            }, bottomBar = {
                Surface(modifier = Modifier.fillMaxSize(), shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppTextButton(
                            onClick = onDismiss,
                            enabled = !triggerCrop,
                            text = stringResource(R.string.btn_cancel),
                            modifier = Modifier.weight(1f)
                        )

                        AppPrimaryButton(
                            onClick = { triggerCrop = true },
                            enabled = !triggerCrop,
                            loading = triggerCrop,
                            text = stringResource(R.string.btn_confirm),
                            icon = Icons.Default.Done,
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    ImageCropper(
                        imageUri = imageUri,
                        cropOptions = cropOptions,
                        cropTrigger = triggerCrop,
                        onCropComplete = { result ->
                            triggerCrop = false // reset
                            if (result.isSuccessful) {
                                result.uriContent?.let { onCropSuccess(it) }
                                    ?: onCropFailure("Uri is Null")
                            } else {
                                onCropFailure(result.error?.message ?: "Unknown Error")
                            }
                        })
                }
            }
        }
    }
}