package top.fseasy.imlog.features

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.home.main.HomeRoute
import top.fseasy.imlog.features.view.ViewScreen

enum class MainTab(
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
  HOME(Icons.Default.Home, R.string.nav_home),
  SETTINGS(Icons.Default.Settings, R.string.nav_dashboard),
}

@Composable
fun MainTabsRoute(
    onNavigateToAppSettings: () -> Unit,
    onNavigateToTopic: (TopicId) -> Unit,
    onNavigateToTopicSettings: (TopicId) -> Unit,
    onNavigateToCreateTopic: () -> Unit,
) {
  var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

  val saveableStateHolder = rememberSaveableStateHolder()

  Scaffold(
      // NOTE: leave the WindowInsetsSides.Top to sub tab page, get more flexibility.
      //       AS the best practise.
      contentWindowInsets =
          WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
      bottomBar = {
        Column(modifier = Modifier.fillMaxWidth()) {
          HorizontalDivider(
              thickness = DividerDefaults.Thickness,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
          )
          NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              tonalElevation = 0.dp,
              modifier = Modifier,
          ) {
            MainTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationBarItem(
                  selected = selected,
                  onClick = { currentTab = tab },
                  icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.labelRes),
                    )
                  },
                  label = { Text(stringResource(tab.labelRes)) },
                  alwaysShowLabel = true,
                  colors =
                      NavigationBarItemDefaults.colors(
                          indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                          selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                          selectedTextColor = MaterialTheme.colorScheme.primary,
                          unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                          unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      ),
              )
            }
          }
        }
      },
  ) { paddingValues ->
    // Material 3 Standard: Fade-Through
    AnimatedContent(
        targetState = currentTab,
        modifier =
            Modifier.fillMaxSize()
                .padding(paddingValues)
                // Avoid duplicated empty space
                .consumeWindowInsets(paddingValues),
        transitionSpec = {
          // enter: 60ms start，duration= 220ms, zoom 96% to 100% and fade in
          val enterTransition =
              fadeIn(
                  animationSpec =
                      tween(
                          durationMillis = 220,
                          delayMillis = 60,
                          easing = LinearOutSlowInEasing,
                      )
              ) +
                  scaleIn(
                      initialScale = 0.96f,
                      animationSpec =
                          tween(
                              durationMillis = 220,
                              delayMillis = 60,
                              easing = LinearOutSlowInEasing,
                          ),
                  )

          // exit：90ms fade out
          val exitTransition =
              fadeOut(animationSpec = tween(durationMillis = 90, easing = FastOutLinearInEasing))

          enterTransition togetherWith exitTransition
        },
        label = "MainTabTransition",
    ) { targetTab ->
      saveableStateHolder.SaveableStateProvider(key = targetTab) {
        when (targetTab) {
          MainTab.HOME -> {
            HomeRoute(
                onNavigateToTopic = onNavigateToTopic,
                onNavigateToAppSettings = onNavigateToAppSettings,
                onNavigateToTopicSettings = onNavigateToTopicSettings,
                onNavigateToCreateTopic = onNavigateToCreateTopic,
            )
          }
          MainTab.SETTINGS -> {
            ViewScreen()
          }
        }
      }
    }
  }
}
