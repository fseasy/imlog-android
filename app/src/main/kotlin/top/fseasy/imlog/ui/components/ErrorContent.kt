package top.fseasy.imlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R

@Composable
fun InternalErrorContent(
    errorMessage: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    isRetryLoading: Boolean = false,
    isRetryEnabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${stringResource(R.string.internal_error_head)}: $errorMessage",
            style = MaterialTheme.typography.bodyMedium
        )
        if (onRetry != null) {
            AppTextButton(
                onClick = onRetry,
                text = stringResource(R.string.btn_retry),
                enabled = isRetryEnabled,
                isLoading = isRetryLoading
            )
        }
    }
}