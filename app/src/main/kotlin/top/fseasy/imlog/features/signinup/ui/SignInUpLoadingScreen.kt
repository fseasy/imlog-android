package top.fseasy.imlog.features.signinup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.signinup.SignInUpRoute
import top.fseasy.imlog.features.signinup.SignInUpUiState

@Composable
fun SignInUpLoadingScreen(
    uiState: SignInUpUiState,
    onNavigate: (SignInUpRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState) {
        when {
            uiState.isLoading || uiState.error != null -> {}
            uiState.users.isEmpty() -> onNavigate(SignInUpRoute.CreateUser)
            else -> onNavigate(SignInUpRoute.SelectUser)
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Load Local Users failed",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}