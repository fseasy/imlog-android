package top.fseasy.imlog.features.signinup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.time.delay
import top.fseasy.imlog.R
import top.fseasy.imlog.features.signinup.CreateUserState
import top.fseasy.imlog.features.signinup.SampledUserProfile
import top.fseasy.imlog.features.signinup.SignInUpUiState
import top.fseasy.imlog.ui.components.AppPrimaryButton
import top.fseasy.imlog.ui.components.AppTextButton
import top.fseasy.imlog.ui.components.UserAvatar
import top.fseasy.imlog.ui.theme.errorSmall
import java.time.Duration

@Composable
fun SignInUpCreateUserScreen(
    uiState: CreateUserState?,
    onCreateClick: (SampledUserProfile) -> Unit,
    onSampleUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(Duration.ofMillis(100))
        onSampleUser()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.signinup_create_account_intro),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState == null || uiState.isLoading -> {
                CircularProgressIndicator()
            }

            uiState.sampledUser != null -> {
                val user = uiState.sampledUser

                UserAvatar(user.avatar)
                Text(user.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.signinup_create_user_sample_success_tip),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.createError == null) {
                    AppPrimaryButton(onClick = { onCreateClick(user) }) {
                        Text(stringResource(R.string.btn_continue))
                    }
                } else {
                    ErrorMessage(uiState.createError, onRetry = { onCreateClick(user) })
                }
                // once create done,
                // the AppInit part will refresh this page and redirect to next step
            }

            uiState.sampleError != null -> {
                ErrorMessage(
                    message = uiState.sampleError, onRetry = onSampleUser
                )
            }

            // Unknown condition
            else -> {
                ErrorMessage(message = "Unknown condition", onRetry = null)
            }
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String?,
    onRetry: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${stringResource(R.string.internal_error_head)}: ${message ?: "Unknown error"}",
            style = MaterialTheme.typography.errorSmall
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AppTextButton(onClick = onRetry, text = stringResource(R.string.btn_retry))
        }
    }
}