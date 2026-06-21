package top.fseasy.imlog.features.signinup.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.fseasy.imlog.features.signinup.LocalUser
import top.fseasy.imlog.ui.components.AppTextButton
import top.fseasy.imlog.ui.components.UserAvatar
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.features.signinup.SignInUpSelectUserUiState
import top.fseasy.imlog.features.signinup.SignInUpSelectUserViewModel
import top.fseasy.imlog.ui.model.TaskExecuteState
import top.fseasy.imlog.ui.model.UserAvatarUiModel

private const val USER_NUM_OF_EACH_ROW = 2;
private const val CARD_MAX_WIDTH = 150;

@Composable
fun SignInUpSelectUserScreen(
    signedInUsers: List<LocalUser>,
    onNavigateToCreateUser: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInUpSelectUserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SelectUserContent(
        uiState = uiState,
        signedInUsers = signedInUsers,
        onSelectUserClick = { uid -> viewModel.selectUser(uid) },
        onNavigateToCreateUser = onNavigateToCreateUser,
        onErrorDismiss = { viewModel.onErrorDismiss() },
        modifier = modifier
    )
}

@Composable
private fun SelectUserContent(
    uiState: SignInUpSelectUserUiState,
    signedInUsers: List<LocalUser>,
    onSelectUserClick: (UserId) -> Unit,
    onNavigateToCreateUser: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier,
) {
    val chunkedUsers = remember(signedInUsers) { signedInUsers.chunked(USER_NUM_OF_EACH_ROW) }
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectState = uiState.selectUserState
    LaunchedEffect(selectState) {
        if (selectState is TaskExecuteState.Failure) {
            snackbarHostState.showSnackbar(message = selectState.reason)
            onErrorDismiss()
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState), verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.signinup_signup_welcome),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.signinup_signup_select_account),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    chunkedUsers.forEachIndexed { rowIndex, rowUsers ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowUsers.forEachIndexed { index, user ->
                                val isCurrentCardLoading =
                                    uiState.selectedUserId == user.id && uiState.selectUserState == TaskExecuteState.Executing
                                val shouldDisable =
                                    uiState.selectUserState == TaskExecuteState.Executing
                                GridUserCard(
                                    user = user,
                                    onClick = { onSelectUserClick(user.id) },
                                    isLoading = isCurrentCardLoading,
                                    enabled = !shouldDisable,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .widthIn(max = CARD_MAX_WIDTH.dp) // 限制卡片最大宽度
                                )
                                if (index < rowUsers.lastIndex) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }

                            if (rowIndex > 0) {
                                // 如果多行，则尾行在填不满时，在右侧放置等宽的隐形占位符，实现靠左对齐的效果
                                repeat(USER_NUM_OF_EACH_ROW - rowUsers.size) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .widthIn(max = CARD_MAX_WIDTH.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                AppTextButton(
                    onClick = onNavigateToCreateUser,
                    text = stringResource(R.string.signinup_goto_create_user),
                    enabled = uiState.selectUserState is TaskExecuteState.Idle
                )
            }
        }
    }
}


@Composable
private fun GridUserCard(
    user: LocalUser,
    onClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // downgrade the alpha
    val contentAlpha = if (enabled || isLoading) 1.0f else 0.5f
    Card(
        onClick = onClick, enabled = enabled && !isLoading, colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ), shape = MaterialTheme.shapes.large, modifier = modifier.alpha(contentAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoadingAwareAvatar(user.avatar, isLoading = isLoading, modifier = modifier)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LoadingAwareAvatar(
    avatar: UserAvatarUiModel,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(64.dp), contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = isLoading, animationSpec = tween(300)) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                UserAvatar(
                    model = avatar, modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}
