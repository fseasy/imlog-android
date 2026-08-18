package top.fseasy.imlog.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import top.fseasy.imlog.features.home.createtopic.CreateTopicRoute
import top.fseasy.imlog.features.home.main.HomeRoute
import top.fseasy.imlog.features.home.topiclog.TopicLogRoute
import top.fseasy.imlog.features.home.topicsettings.TopicSettingsSheet
import top.fseasy.imlog.features.settings.AboutScreen
import top.fseasy.imlog.features.settings.FeedbackScreen
import top.fseasy.imlog.features.view.ViewScreen

/** Main root navigation key */
@Serializable data object MainGraph

sealed interface MainScreen {
  @Serializable data object Home : MainScreen

  /**
   * NOTE: it's very error-prone to define custom-types in nav key, as you need to write typeMap in
   * composable<> and SavedStateHandle.toRoute, any missing you'll get a runtime crash...
   *
   * I give up, use the plain data type to avoid it.
   */
  @Serializable data class TopicTimeline(val topicId: String) : MainScreen

  @Serializable data class TopicSettings(val topicId: String) : MainScreen

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
            navController.navigate(MainScreen.TopicTimeline(topicId.value))
          },
          onNavigateToAppSettings = onOpenDrawer,
          onNavigateToTopicSettings = { topicId ->
            navController.navigate(MainScreen.TopicSettings(topicId.value))
          },
          onNavigateToCreateTopic = { navController.navigate(MainScreen.CreateTopic) },
      )
    }
    composable<MainScreen.TopicTimeline> {
      TopicLogRoute(
          onNavigateBack = { navController.popBackStack() },
          onSettingsClick = { topicId ->
            navController.navigate(MainScreen.TopicSettings(topicId.value))
          },
      )
    }
    composable<MainScreen.TopicSettings> {
      TopicSettingsSheet(
          onBack = { navController.popBackStack() },
          afterDeleteNavigate = {
            navController.popBackStack(MainScreen.Home, inclusive = false)
          },
      )
    }

    composable<MainScreen.Dashboard> {
      ViewScreen()
    }

    composable<MainScreen.CreateTopic> {
      CreateTopicRoute(
          onNavigateBack = { navController.popBackStack() },
          onNavigateToNewTopic = { topicId ->
            navController.navigate(MainScreen.TopicTimeline(topicId.value)) {
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
