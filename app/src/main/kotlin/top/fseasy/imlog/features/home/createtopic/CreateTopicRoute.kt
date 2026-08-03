package top.fseasy.imlog.features.home.createtopic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.AppImageFormat
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.ui.components.CropImageDialog
import top.fseasy.imlog.ui.model.AvatarUiModel
import top.fseasy.imlog.ui.model.TaskExecuteWithDefaultState
import top.fseasy.imlog.ui.model.TopicAvatarUiModel

@Composable
fun CreateTopicRoute(
    onNavigateBack: () -> Unit,
    onNavigateToNewTopic: (TopicId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateTopicViewModel = hiltViewModel(),
) {

  val snackbarHostState = remember { SnackbarHostState() }
  val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.effect.collect { effect ->
      when (effect) {
        is CreateTopicEffect.NavigateToTopic -> {
          onNavigateToNewTopic(effect.topicId)
        }
        is CreateTopicEffect.ShowSnackBar -> {
          snackbarHostState.showSnackbar(
              message = effect.message,
              duration = SnackbarDuration.Short,
          )
        }
      }
    }
  }

  CreateTopicContent(
      uiState = uiState,
      avatarImageOutputFormat = viewModel.avatarOutputFormat,
      snackbarHostState = snackbarHostState,
      onDismiss = onNavigateBack,
      onCreate = { name, description, avatar ->
        viewModel.createTopic(
            name = name,
            description = description,
            avatarUiModel = avatar,
        )
      },
      onSelectPresetAvatar = { avatar -> viewModel.selectPresetAvatar(avatar) },
      onSelectAvatarFromUri = { uri -> viewModel.selectAvatarFromUri(uri) },
      onCropAvatarFailed = { errorMessage ->
        viewModel.showSnackBarWhenCropAvatarFailed(errorMessage)
      },
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTopicContent(
    uiState: CreateTopicUiState,
    avatarImageOutputFormat: AppImageFormat,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, avatar: TopicAvatarUiModel) -> Unit,
    onSelectPresetAvatar: (AvatarUiModel.Preset<TopicPresetAvatar>) -> Unit,
    onSelectAvatarFromUri: (Uri) -> Unit,
    onCropAvatarFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  var topicName by rememberSaveable { mutableStateOf("") }
  var topicDescription by rememberSaveable { mutableStateOf("") }

  var showBottomSheet by rememberSaveable { mutableStateOf(false) }
  var showCropDialog by rememberSaveable { mutableStateOf(false) }
  var rawGalleryUri by rememberSaveable { mutableStateOf<Uri?>(null) }

  val photoPickerLauncher =
      rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri
        ->
        if (uri != null) {
          rawGalleryUri = uri
          showCropDialog = true
        }
      }

  Scaffold(
      modifier = modifier,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.create_topic_title)) },
            navigationIcon = {
              IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back),
                )
              }
            },
        )
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
      // -------------------------------------------------------------
      // WhatsApp style:
      // | Avatar (72dp) | Topic Name Textfield        |
      // | (With badge)  | Topic Description Textfield |
      // -------------------------------------------------------------
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.Top,
      ) {
        TopicAvatarBadgeView(
            avatarTask = uiState.selectAvatarTask,
            onClick = { showBottomSheet = true },
            modifier = Modifier.padding(top = 8.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
              value = topicName,
              onValueChange = { topicName = it },
              label = { Text(stringResource(R.string.create_topic_label_topic_name)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
              value = topicDescription,
              onValueChange = { topicDescription = it },
              label = { Text(stringResource(R.string.create_topic_label_topic_desc)) },
              minLines = 2,
              maxLines = 4,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      Button(
          onClick = { onCreate(topicName, topicDescription, uiState.selectAvatarTask.data) },
          // if it's executing, the value isn't the final state
          enabled =
              topicName.isNotBlank() &&
                  uiState.selectAvatarTask !is TaskExecuteWithDefaultState.Executing,
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      ) {
        Text("Create")
      }
    }

    if (showBottomSheet) {
      TopicAvatarPickerBottomSheet(
          currentAvatar = uiState.selectAvatarTask.data,
          onDismissRequest = { showBottomSheet = false },
          onSelectPreset = onSelectPresetAvatar,
          onPickFromGallery = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
      )
    }

    if (showCropDialog && rawGalleryUri != null) {
      CropImageDialog(
          title = stringResource(R.string.create_topic_crop_image_title),
          imageUri = rawGalleryUri as Uri,
          outputFormat = avatarImageOutputFormat,
          onDismiss = {
            showCropDialog = false
            rawGalleryUri = null
          },
          onCropSuccess = { croppedUri ->
            showCropDialog = false
            rawGalleryUri = null
            onSelectAvatarFromUri(croppedUri)
          },
          onCropFailure = { error ->
            showCropDialog = false
            rawGalleryUri = null
            onCropAvatarFailed(error)
          },
      )
    }
  }
}
