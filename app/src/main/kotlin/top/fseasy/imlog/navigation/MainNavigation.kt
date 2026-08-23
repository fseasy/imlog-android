package top.fseasy.imlog.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import top.fseasy.imlog.features.MainTabsRoute
import top.fseasy.imlog.features.home.createtopic.CreateTopicRoute
import top.fseasy.imlog.features.home.topiclog.TopicLogRoute
import top.fseasy.imlog.features.home.topicsettings.TopicSettingsSheet
import top.fseasy.imlog.features.settings.AboutScreen
import top.fseasy.imlog.features.settings.AppSettingsRoute
import top.fseasy.imlog.features.settings.FeedbackScreen

/** Main root navigation key */
@Serializable data object MainGraph

sealed interface MainScreen {
  @Serializable data object MainTabs : MainScreen

  /**
   * NOTE: it's very error-prone to define custom-types in nav key, as you need to write typeMap in
   * composable<> and SavedStateHandle.toRoute, any missing you'll get a runtime crash...
   *
   * I give up, use the plain data type to avoid it.
   */
  @Serializable data class TopicLog(val topicId: String) : MainScreen

  @Serializable data class TopicSettings(val topicId: String) : MainScreen

  @Serializable data object CreateTopic : MainScreen

  @Serializable data object AppSettings : MainScreen

  @Serializable data object Feedback : MainScreen

  @Serializable data object About : MainScreen
}

fun NavGraphBuilder.mainGraph(
    navController: NavController,
    onSignedOutNavigate: () -> Unit,
) {

  fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOutAnimation() =
      slideOutOfContainer(
          towards = AnimatedContentTransitionScope.SlideDirection.End,
          animationSpec = tween(300),
      )

  navigation<MainGraph>(startDestination = MainScreen.MainTabs) {
    composable<MainScreen.MainTabs> {
      MainTabsRoute(
          onNavigateToTopic = { topicId ->
            navController.navigate(MainScreen.TopicLog(topicId.value))
          },
          onNavigateToAppSettings = { navController.navigate(MainScreen.AppSettings) },
          onNavigateToTopicSettings = { topicId ->
            navController.navigate(MainScreen.TopicSettings(topicId.value))
          },
          onNavigateToCreateTopic = { navController.navigate(MainScreen.CreateTopic) },
      )
    }
    composable<MainScreen.TopicLog>(
        exitTransition = { slideOutAnimation() },
        popExitTransition = { slideOutAnimation() },
    ) {
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
            navController.popBackStack(MainScreen.MainTabs, inclusive = false)
          },
      )
    }

    composable<MainScreen.CreateTopic> {
      CreateTopicRoute(
          onNavigateBack = { navController.popBackStack() },
          onNavigateToNewTopic = { topicId ->
            navController.navigate(MainScreen.TopicLog(topicId.value)) {
              popUpTo(MainScreen.CreateTopic) { inclusive = true }
            }
          },
      )
    }

    composable<MainScreen.AppSettings> {
      AppSettingsRoute(
          onNavigateBack = { navController.popBackStack() },
          onNavigateToAbout = { navController.navigate(MainScreen.About) },
          onNavigateToFeedback = { navController.navigate(MainScreen.Feedback) },
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
