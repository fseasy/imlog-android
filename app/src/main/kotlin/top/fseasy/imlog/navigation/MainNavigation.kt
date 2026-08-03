package top.fseasy.imlog.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.home.createtopic.CreateTopicRoute
import top.fseasy.imlog.features.home.main.HomeRoute
import top.fseasy.imlog.features.home.topiclog.TopicLogScreen
import top.fseasy.imlog.features.home.topicsettings.TopicSettingsSheet
import top.fseasy.imlog.features.settings.AboutScreen
import top.fseasy.imlog.features.settings.FeedbackScreen
import top.fseasy.imlog.features.view.ViewScreen

/** Main root navigation key */
@Serializable data object MainGraph

sealed interface MainScreen {
  @Serializable data object Home : MainScreen

  @Serializable data class TopicTimeline(val topicId: TopicId) : MainScreen

  @Serializable data class TopicSettings(val topicId: TopicId) : MainScreen

  @Serializable data object CreateTopic : MainScreen

  @Serializable data object Dashboard : MainScreen

  @Serializable data object AppSettings : MainScreen

  @Serializable data object Feedback : MainScreen

  @Serializable data object About : MainScreen
}

fun NavGraphBuilder.mainGraph(
    navController: NavController,
    onOpenDrawer: () -> Unit,
    onSignedOutNavigate: () -> Unit,
) {
  navigation<MainGraph>(startDestination = MainScreen.Home) {
    composable<MainScreen.Home> {
      HomeRoute(
          onNavigateToTopic = { topicId ->
            navController.navigate(MainScreen.TopicTimeline(topicId))
          },
          onNavigateToAppSettings = onOpenDrawer,
          onNavigateToTopicSettings = { topicId ->
            navController.navigate(MainScreen.TopicSettings(topicId))
          },
          onNavigateToCreateTopic = { navController.navigate(MainScreen.CreateTopic) },
      )
    }
    composable<MainScreen.TopicTimeline> {
      TopicLogScreen(
          onNavigateBack = { navController.popBackStack() },
          onSettingsClick = { topicId ->
            navController.navigate(MainScreen.TopicSettings(topicId))
          },
      )
    }
    composable<MainScreen.Dashboard> {
      ViewScreen()
    }
    composable<MainScreen.TopicSettings> {
      TopicSettingsSheet(
          onBack = { navController.popBackStack() },
          afterDeleteNavigate = {
            navController.popBackStack(MainScreen.Home, inclusive = false)
          },
      )
    }
    composable<MainScreen.CreateTopic> {
      CreateTopicRoute(
          onNavigateBack = { navController.popBackStack() },
          onNavigateToNewTopic = { topicId ->
            navController.navigate(MainScreen.TopicTimeline(topicId)) {
              popUpTo(MainScreen.CreateTopic) { inclusive = true }
            }
          },
      )
    }

    composable<MainScreen.Feedback> {
      FeedbackScreen(onBack = { navController.popBackStack() })
    }
    composable<MainScreen.About> {
      AboutScreen(onBack = { navController.popBackStack() })
    }
  }
}
