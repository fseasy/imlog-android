package top.fseasy.imlog.features.home.topiclog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.home.topiclog.composer.MessageComposer
import top.fseasy.imlog.features.home.topiclog.composer.MessageComposerViewModel
import top.fseasy.imlog.features.home.topiclog.fullscreencontainer.FullScreenContainer
import top.fseasy.imlog.features.home.topiclog.fullscreencontainer.FullScreenContainerUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageTimeline
import top.fseasy.imlog.ui.components.overlaylayout.OverlayLayout
import top.fseasy.imlog.ui.util.openFileWithChooser

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TopicLogRoute(
    onNavigateBack: () -> Unit,
    onSettingsClick: (TopicId) -> Unit,
    viewModel: TopicLogViewModel = hiltViewModel(),
    // Manage composer viewmodel here as we need to call its clearInputMode
    composerViewModel: MessageComposerViewModel = hiltViewModel(),
) {
  val topicName =
      (viewModel.contextStateFlow.collectAsStateWithLifecycle().value as? ContextState.Success)
          ?.topic
          ?.name
  val focusManager = LocalFocusManager.current

  // Used to close composer and reset composer input mode. It has to be set in the parent level of
  // the composer
  val handleComposerDismiss = {
    focusManager.clearFocus()
    composerViewModel.clearInputMode()
  }

  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  // MessageUiModel supports parcelable, so it's ok to use rememberSavable!
  var currentFullScreenViewMessage by
      rememberSaveable() { mutableStateOf<FullScreenContainerUiModel?>(null) }

  // Effect listener, belongs to Smart level
  LaunchedEffect(Unit) {
    viewModel.uiEffect.collect { e ->
      when (e) {
        is TopicLogUiEffect.OpenFileChooser -> {
          openFileWithChooser(
              context = context,
              uri = e.uri,
              mimeType = e.mimeType,
              fileDisplayName = e.displayName,
          )
        }
        is TopicLogUiEffect.ShowSnackBar -> {
          snackbarHostState.showSnackbar(e.message)
        }

        is TopicLogUiEffect.SetFullScreenViewMessage ->
            currentFullScreenViewMessage = e.fullScreenMessage
      }
    }
  }

  val mediaPlaybackStateAndAction =
      MediaPlaybackStateAndAction(
          activePlaybackStateHolder =
              viewModel.activeMediaPlaybackState.collectAsStateWithLifecycle(),
          activePlayPositionHolder =
              viewModel.activeMediaPlayPosition.collectAsStateWithLifecycle(),
          inactivePlayPositionGetter = viewModel::getMediaCachedPlayPosition,
          onTogglePlay = viewModel::toggleMediaPlay,
          onSeek = viewModel::seekMedia,
          onCyclePlaybackSpeed = viewModel::cycleMediaPlaybackSpeed,
      )

  val showFullScreenMessage =
      ShowFullScreenMessageUiModelAction(
          showImage = viewModel::showImageLikeFullScreenMessage,
          showVideo = viewModel::showImageLikeFullScreenMessage,
          // It's trivial, just do it in UI side
          showTextSelection = { textMessage ->
            currentFullScreenViewMessage = FullScreenContainerUiModel.TextSelection(textMessage)
          },
      )

  OverlayLayout(
      activeItem = currentFullScreenViewMessage,
      itemKey = { m -> toSharedTransitionElementId(m.id) },
      onDismissFinished = { currentFullScreenViewMessage = null },
      content = {
        TopicLogContent(
            topicId = viewModel.topicId,
            topicName = topicName,
            // proxy to composer viewModel to handle the inputMode correctly and then navigate back
            onNavigateBack = composerViewModel::handleNavigationBack,
            onSettingsClick = onSettingsClick,
            timelineSection = { modifier ->
              MessageTimeline(
                  onTapEmptyArea = handleComposerDismiss,
                  onDragList = handleComposerDismiss,
                  onShowFullScreenMessage = showFullScreenMessage,
                  mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
                  onOpenFile = viewModel::createOpenFileIntentForGenericFileMessage,
                  modifier = modifier,
              )
            },
            composerSection = {
              MessageComposer(
                  onNavigateBack = onNavigateBack,
                  onShowSnackbar = viewModel::showSnackbar,
                  viewModel = composerViewModel,
              )
            },
            snackbarHostState = snackbarHostState,
        )
      },
  ) { fullScreenViewMessage ->
    FullScreenContainer(
        model = fullScreenViewMessage,
        player = viewModel.player,
        mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicLogContent(
    topicId: TopicId,
    topicName: String?,
    onNavigateBack: () -> Unit,
    onSettingsClick: (TopicId) -> Unit,
    timelineSection: @Composable (modifier: Modifier) -> Unit,
    composerSection: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(topicName ?: stringResource(R.string.common_ui_text_loading_dots))
            },
            navigationIcon = {
              IconButton(onClick = { onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
              }
            },
            actions = {
              IconButton(onClick = { onSettingsClick(topicId) }) {
                Icon(Icons.Default.Settings, stringResource(R.string.btn_setting))
              }
            },
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      modifier = modifier.fillMaxSize(),
  ) { paddingValues ->
    val topPadding = paddingValues.calculateTopPadding()
    Column(
        modifier =
            Modifier.fillMaxSize()
                // NOTE: only consider top padding, leaving the bottom padding to the composer
                .padding(top = topPadding)
                .consumeWindowInsets(PaddingValues(top = topPadding))
    ) {
      timelineSection(Modifier.weight(1f))

      composerSection()
    }
  }
}
