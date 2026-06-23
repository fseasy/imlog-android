package top.fseasy.imlog.features.appinit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.ui.model.TaskExecuteState
import top.fseasy.imlog.features.appinit.WelcomeUiState
import top.fseasy.imlog.features.appinit.WelcomeViewModel
import top.fseasy.imlog.ui.components.AppPrimaryButton
import top.fseasy.imlog.ui.components.AppTextButton
import top.fseasy.imlog.ui.components.HighlightConfig
import top.fseasy.imlog.ui.components.HighlightedText
import top.fseasy.imlog.ui.components.InternalErrorContent


@Composable
fun WelcomeScreen(
    userId: UserId,
    needCreateFirstTopic: Boolean,
    onSuccessNavigate: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (needCreateFirstTopic) viewModel.autoCreateFirstTopic(userId)
    }

    WelcomeEntry(
        needCreateFirstTopic = needCreateFirstTopic,
        uiState = uiState,
        onSuccessNavigate = onSuccessNavigate,
        onCreateTopicRetryClick = { viewModel.triggerCreateFirstTopic(userId) },
        onStartClick = { viewModel.markWelcomeShown(userId) })
}

@Composable
fun WelcomeEntry(
    needCreateFirstTopic: Boolean,
    uiState: WelcomeUiState,
    onSuccessNavigate: () -> Unit,
    onCreateTopicRetryClick: () -> Unit,
    onStartClick: () -> Unit,
) {
    LaunchedEffect(uiState.markWelcomeState) {
        if (uiState.markWelcomeState is TaskExecuteState.Success) {
            onSuccessNavigate()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HighlightedText(
            stringResource(R.string.welcome_headline),
            style = MaterialTheme.typography.headlineMedium,
            highlight = HighlightConfig(),
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(30.dp))

        if (needCreateFirstTopic) {
            when (val state = uiState.topicCreateState) {
                is TaskExecuteState.Idle -> Text("Preparing to Create the first topic for you")
                is TaskExecuteState.Executing -> Text("Creating the first topic")
                is TaskExecuteState.Success -> WelcomeContent(uiState, onStartClick)
                is TaskExecuteState.Failure -> InternalErrorContent(
                    state.reason, onRetry = onCreateTopicRetryClick
                )
            }
        } else {
            WelcomeContent(uiState, onStartClick)
        }
    }
}

@Composable
fun WelcomeContent(
    uiState: WelcomeUiState,
    onStartClick: () -> Unit,
) {
    Text(
        stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyMedium
    )

    Spacer(Modifier.height(30.dp))

    when (val state = uiState.markWelcomeState) {
        is TaskExecuteState.Idle,
        is TaskExecuteState.Executing,
        is TaskExecuteState.Success,
            -> AppPrimaryButton(
            onClick = onStartClick,
            loading = state != TaskExecuteState.Idle,
            text = stringResource(R.string.btn_start)
        )

        is TaskExecuteState.Failure -> {
            InternalErrorContent(state.reason, onRetry = onStartClick)
        }
    }
}