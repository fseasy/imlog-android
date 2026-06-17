package top.fseasy.imlog.features.signinup.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.signinup.LocalUser
import top.fseasy.imlog.ui.components.AppTextButton
import top.fseasy.imlog.ui.components.UserAvatar
import top.fseasy.imlog.R

private const val USER_NUM_OF_EACH_ROW = 2;
private const val CARD_MAX_WIDTH = 150;

@Composable
fun SignInUpSelectUserScreen(
    signedInUsers: List<LocalUser>,
    onSelectUserClick: (LocalUser) -> Unit,
    onNavigateToCreateUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chunkedUsers = remember(signedInUsers) { signedInUsers.chunked(USER_NUM_OF_EACH_ROW) }
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState), verticalArrangement = Arrangement.Center
        ) {
            // 1. 头部标题
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally
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
                            GridUserCard(
                                user = user,
                                onClick = { onSelectUserClick(user) },
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
                text = stringResource(R.string.signinup_goto_create_user)
            )
        }
    }
}


@Composable
private fun GridUserCard(
    user: LocalUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick, colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ), shape = MaterialTheme.shapes.large, modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserAvatar(
                model = user.avatar, modifier = Modifier.size(64.dp)
            )
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