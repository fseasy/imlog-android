package top.fseasy.imlog.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToFeedback: () -> Unit,
) {
  var showEditProfile by remember { mutableStateOf(false) }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(stringResource(R.string.feature_app_settings_title))
            },
            navigationIcon = {
              IconButton(onClick = { onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
              }
            },
        )
      },
      modifier = Modifier.fillMaxWidth(),
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
      Text(
          text = "Settings",
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(bottom = 24.dp),
      )

      // Profile Section
      Row(
          modifier =
              Modifier.fillMaxWidth().clickable { showEditProfile = true }.padding(vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
            modifier = Modifier.size(56.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
        ) {
          androidx.compose.foundation.layout.Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
          }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Icon(painterResource(R.drawable.icon_chevron_right), contentDescription = null)
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

      // Settings Options
      SettingsItem(
          icon = ImageVector.vectorResource(R.drawable.icon_help),
          title = "Feedback",
          subtitle = "Send us your feedback",
          onClick = onNavigateToFeedback,
      )

      SettingsItem(
          icon = Icons.Default.Info,
          title = "About ImTrace",
          subtitle = "Version 1.0.0",
          onClick = onNavigateToAbout,
      )

      Spacer(modifier = Modifier.height(24.dp))
    }
  }

  if (showEditProfile) {
    OnboardingDialog(
        onDismiss = { showEditProfile = false },
        onConfirm = { username ->
          //                viewModel.createUser(username)
          showEditProfile = false
        },
    )
  }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(16.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
      )
      Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(
        painterResource(R.drawable.icon_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
