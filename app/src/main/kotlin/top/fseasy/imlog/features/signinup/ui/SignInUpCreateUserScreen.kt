package top.fseasy.imlog.features.signinup.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R
import top.fseasy.imlog.features.signinup.CreateUserState
import top.fseasy.imlog.ui.components.AnimatedTextLineConfig
import top.fseasy.imlog.ui.components.HighlightConfig
import top.fseasy.imlog.ui.components.InternalErrorInfoText
import top.fseasy.imlog.ui.components.StackedAnimatedText
import top.fseasy.imlog.ui.components.StackedAnimationTiming

@Composable
fun SignInUpCreateUserScreen(
    uiState: CreateUserState?,
    onCreateUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        onCreateUser()
    }

    OnboardingIntroContent(uiState, modifier = modifier)
}

@Composable
fun OnboardingIntroContent(uiState: CreateUserState?, modifier: Modifier = Modifier) {
    val text1 = stringResource(R.string.signinup_signin_intro_line1)
    val text2 = stringResource(R.string.signinup_signin_intro_line2)

    when (uiState) {
        is CreateUserState.Failure -> {
            InternalErrorInfoText(uiState.message)
        }

        else -> {
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
        }
    }

}

@Preview
@Composable
fun PreviewOnboardingIntroContent() {
    OnboardingIntroContent(null)
}