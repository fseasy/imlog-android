package top.fseasy.imlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.ui.model.TaskExecuteState

@Composable
fun AppCircularProgress(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
    loadingDisplayMessage: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size), color = color, strokeWidth = strokeWidth
        )
        if (!loadingDisplayMessage.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = loadingDisplayMessage,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun LoadingContentWrapper(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    loadingDisplayMessage: String? = null,
    loadingContent: @Composable () -> Unit = {
        Box(
            modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            AppCircularProgress(loadingDisplayMessage = loadingDisplayMessage)
        }
    },
    content: @Composable () -> Unit,
) {
    if (isLoading) {
        loadingContent()
    } else {
        content()
    }
}

/**
 * @param failureContent - if null, same to the loading content; else will pass the error reason to the lambda
 * @param successContent - will receive the Success data.
 */
@Composable
fun <T> TaskStateLoadingWrapper(
    state: TaskExecuteState<T>,
    modifier: Modifier = Modifier,
    loadingDisplayMessage: String? = null,
    loadingContent: @Composable () -> Unit = {
        Box(
            modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            AppCircularProgress(loadingDisplayMessage = loadingDisplayMessage)
        }
    },
    failureContent: @Composable ((String) -> Unit)? = null,
    successContent: @Composable (T) -> Unit,
) {
    when (state) {
        is TaskExecuteState.Idle,
        is TaskExecuteState.Executing,
            -> loadingContent()

        is TaskExecuteState.Failure -> {
            if (failureContent != null) {
                failureContent(state.reason)
            } else {
                loadingContent()
            }
        }

        is TaskExecuteState.Success -> {
            successContent(state.data)
        }
    }
}