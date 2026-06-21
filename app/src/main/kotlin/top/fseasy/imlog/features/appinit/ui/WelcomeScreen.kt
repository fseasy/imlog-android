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
import top.fseasy.imlog.domain.repository.StringResId
import top.fseasy.imlog.ui.model.TaskExecuteState
import top.fseasy.imlog.features.appinit.WelcomeUiState
import top.fseasy.imlog.features.appinit.WelcomeViewModel
import top.fseasy.imlog.ui.components.AppPrimaryButton
import top.fseasy.imlog.ui.components.AppTextButton
import top.fseasy.imlog.ui.components.HighlightConfig
import top.fseasy.imlog.ui.components.HighlightedText
import top.fseasy.imlog.ui.components.InternalErrorInfoText


@Composable
fun WelcomeScreen(
    userId: UserId,
    shouldCreatingFirstTopic: Boolean,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (shouldCreatingFirstTopic) viewModel.createFirstTopic(userId)
    }

    WelcomeContent(
        userId = userId,
        isCreatingFirstTopic = shouldCreatingFirstTopic,
        uiState = uiState,
        onEnterClick = { viewModel.markWelcomeDone(userId) })
}

@Composable
fun WelcomeContent(
    userId: UserId,
    isCreatingFirstTopic: Boolean,
    uiState: WelcomeUiState,
    onEnterClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HighlightedText(
            stringResource(R.string.welcome_headline),
            style = MaterialTheme.typography.headlineMedium,
            highlight = HighlightConfig(),
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(30.dp))

        if (isCreatingFirstTopic) {
            when (val state = uiState.topicCreateState) {
                is TaskExecuteState.Idle -> Text("Preparing to Create the first topic for you")
                is TaskExecuteState.Executing -> Text("Creating the first topic")
                is TaskExecuteState.Success -> Text("Creating success")
                is TaskExecuteState.Failure -> InternalErrorInfoText(state.reason)
            }
        } else {
            Text(
                stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(30.dp))

            when (val state = uiState.markWelcomeState) {
                is TaskExecuteState.Idle,
                is TaskExecuteState.Executing,
                is TaskExecuteState.Success,
                    -> AppPrimaryButton(
                    onClick = onEnterClick,
                    loading = state != TaskExecuteState.Idle,
                    text = stringResource(R.string.btn_start)
                )

                is TaskExecuteState.Failure -> {
                    InternalErrorInfoText(state.reason)
                    AppTextButton(onClick = onEnterClick, text = stringResource(R.string.btn_retry))
                }
            }
        }
    }
}