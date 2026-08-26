package top.fseasy.imlog.features.home.topiclog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.home.topiclog.composer.MessageComposer
import top.fseasy.imlog.features.home.topiclog.composer.MessageComposerViewModel
import top.fseasy.imlog.features.home.topiclog.fullscreencontainer.FullScreenContainer
import top.fseasy.imlog.features.home.topiclog.timeline.FullScreenMessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageTimeline
import top.fseasy.imlog.ui.util.openFileWithChooser

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
      rememberSaveable() { mutableStateOf<FullScreenMessageUiModel?>(null) }

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

  val messageListState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  fun messageListScrollToBottom() {
    coroutineScope.launch {
      // too much, directly scroll w/o animation
      if (messageListState.firstVisibleItemIndex > 5) {
        messageListState.scrollToItem(0)
      } else {
        messageListState.animateScrollToItem(0)
      }
    }
  }

  TopicLogSharedTransitionLayoutContainer(
      currentFullScreenViewMessage = currentFullScreenViewMessage,
      logContent = {
        TopicLogContent(
            topicId = viewModel.topicId,
            topicName = topicName,
            // proxy to composer viewModel to handle the inputMode correctly and then navigate back
            onNavigateBack = composerViewModel::handleNavigationBack,
            onSettingsClick = onSettingsClick,
            timelineSection = {
              MessageTimeline(
                  messageListState = messageListState,
                  onTapOutside = handleComposerDismiss,
                  onDragList = handleComposerDismiss,
                  onFullScreenViewMessage = { message ->
                    viewModel.prepareFullScreenViewMessage(message)
                  },
                  mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
                  onOpenFile = viewModel::createOpenFileIntentForGenericFileMessage,
              )
            },
            composerSection = {
              MessageComposer(
                  onNavigateBack = onNavigateBack,
                  onSendMessageCallback = ::messageListScrollToBottom,
                  onShowSnackbar = viewModel::showSnackbar,
                  viewModel = composerViewModel,
              )
            },
            handleComposerDismiss = handleComposerDismiss,
            snackbarHostState = snackbarHostState,
        )
      },
      fullScreenOverlay = { fullScreenViewMessage ->
        FullScreenContainer(
            model = fullScreenViewMessage,
            onClose = { currentFullScreenViewMessage = null },
            player = viewModel.player,
            mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
        )
      },
  )
}

/**
 * For SharedTransitionLayout. It's like a local var, The same compositionLocal can provide
 * different values, so that the composable get the current value according to its position of the
 * component tree - from the nearest parent node that provides the value.
 */
val LocalTopicLogVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** For SharedTransitionLayout. */
val LocalTopicLogSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The container wraps the shared-transition-layout between the LogContent and FullScreen Overlay.
 *
 * NOTE: we pass the scope by `compositionLocalOf` instead of param-passing-through
 */
@Composable
private fun TopicLogSharedTransitionLayoutContainer(
    currentFullScreenViewMessage: FullScreenMessageUiModel?,
    logContent: @Composable () -> Unit,
    fullScreenOverlay: @Composable (message: FullScreenMessageUiModel) -> Unit,
) {

  SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalTopicLogSharedTransitionScope provides this) {
      Box(modifier = Modifier.fillMaxSize()) {
        // Why use a AnimatedVisibility with a constant `visible = true`
        // 1. SharedTransitionLayout must need a animatedVisibility scope!
        // 2. We can't use AnimatedContent as it will distroy the content when swith to the overlay
        // part
        // 3. so finally, we have to hack it to build a dummy always-visible scope.
        // Why top level instead of the level of the leaf node? -> it's the most efficient one, as
        // only 1 scope is created and never destroied as user scroll the message timeline.
        AnimatedVisibility(
            visible = true,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize(),
        ) {
          CompositionLocalProvider(LocalTopicLogVisibilityScope provides this) {
            logContent()
          }
        }
        AnimatedVisibility(
            visible = currentFullScreenViewMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
          CompositionLocalProvider(LocalTopicLogVisibilityScope provides this) {
            currentFullScreenViewMessage?.let {
              fullScreenOverlay(it)
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicLogContent(
    topicId: TopicId,
    topicName: String?,
    onNavigateBack: () -> Unit,
    onSettingsClick: (TopicId) -> Unit,
    timelineSection: @Composable () -> Unit,
    composerSection: @Composable () -> Unit,
    handleComposerDismiss: () -> Unit,
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
    Column(
        modifier =
            Modifier.fillMaxSize()
                //                .padding(top = paddingValues.calculateTopPadding())
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
    ) {
      Box(
          modifier =
              modifier.weight(1f).fillMaxSize().pointerInput(Unit) {
                detectTapGestures {
                  handleComposerDismiss()
                }
              }
      ) {
        timelineSection()
      }

      composerSection()
    }
  }
}
