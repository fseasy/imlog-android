package top.fseasy.imlog.features.auth.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.R
import top.fseasy.imlog.features.auth.SignInUpCreateUserUiState
import top.fseasy.imlog.features.auth.SignInUpCreateUserViewModel
import top.fseasy.imlog.ui.components.AnimatedTextLineConfig
import top.fseasy.imlog.ui.components.HighlightConfig
import top.fseasy.imlog.ui.components.InternalErrorContent
import top.fseasy.imlog.ui.components.StackedAnimatedText
import top.fseasy.imlog.ui.components.StackedAnimationTiming
import top.fseasy.imlog.ui.model.TaskExecuteState

@Composable
fun SignInUpCreateLocalUserScreen(
    onAuthSuccessNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInUpCreateUserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.createUser()
    }

    SignInIntroContent(
        uiState,
        onCreateUserFail = { viewModel.onCreateUserFail() },
        onAuthSuccessNavigate = onAuthSuccessNavigate,
        modifier = modifier
    )
}

@Composable
private fun SignInIntroContent(
    uiState: SignInUpCreateUserUiState,
    onCreateUserFail: () -> Unit,
    onAuthSuccessNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val text1 = stringResource(R.string.signinup_signin_intro_line1)
    val text2 = stringResource(R.string.signinup_signin_intro_line2)

    LaunchedEffect(uiState.createUserState) {
        if (uiState.createUserState is TaskExecuteState.Failure) {
            snackbarHostState.showSnackbar(uiState.createUserState.reason)
            onCreateUserFail()
        } else if (uiState.createUserState is TaskExecuteState.Success) {
            onAuthSuccessNavigate()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->

        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues), color = MaterialTheme.colorScheme.background
        ) {
            StackedAnimatedText(
                textLines = listOf(
                    AnimatedTextLineConfig(
                        text = text1, style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Normal
                        ), durationMs = 1200, stayDurationMs = 800, alignment = TextAlign.Center
                    ), AnimatedTextLineConfig(
                        text = text2, style = MaterialTheme.typography.headlineMedium.copy(
                            color = Color.White,
                            fontSize = 24.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.Bold
                        ), durationMs = 800, stayDurationMs = 3000, // 停留时间长一些
                        alignment = TextAlign.Center, highlight = HighlightConfig(
                            color = Color(0xFFFFD54F), scale = 1.05f
                        )
                    )
                ), timingConfig = StackedAnimationTiming(
                    initialDelay = 300, lineGap = 500, overlap = false
                ), modifier = modifier.fillMaxWidth()
            )
            if (uiState.createUserState is TaskExecuteState.Failure) InternalErrorContent(
                stringResource(R.string.common_ui_unknown_error_and_auto_retry)
            )
        }
    }
}

@Preview
@Composable
fun PreviewSignInIntroContent() {
    SignInIntroContent(
        SignInUpCreateUserUiState(),
        onCreateUserFail = {},
        onAuthSuccessNavigate = {})
}