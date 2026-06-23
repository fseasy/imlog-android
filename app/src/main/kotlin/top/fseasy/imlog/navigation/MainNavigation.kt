package top.fseasy.imlog.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.home.ui.TimelineScreen
import top.fseasy.imlog.features.home.ui.TopicSettingsSheet
import top.fseasy.imlog.features.home.ui.HomeScreen
import top.fseasy.imlog.features.settings.AboutScreen
import top.fseasy.imlog.features.settings.FeedbackScreen
import top.fseasy.imlog.features.view.ViewScreen

/**
 * Main root navigation key
 */
@Serializable
data object MainGraph

sealed interface MainScreen {
    @Serializable
    data object Home : MainScreen

    @Serializable
    data class TopicTimeline(val topicId: TopicId) : MainScreen

    @Serializable
    data class TopicSettings(val topicId: TopicId) : MainScreen

    @Serializable
    data object Dashboard : MainScreen

    @Serializable
    data object Settings : MainScreen

    @Serializable
    data object Feedback : MainScreen

    @Serializable
    data object About : MainScreen
}

fun NavGraphBuilder.mainGraph(
    navController: NavController,
    onOpenDrawer: () -> Unit,
    onSignedOutNavigate: () -> Unit,
) {
    navigation<MainGraph>(startDestination = MainScreen.Home) {
        composable<MainScreen.Home> {
            HomeScreen(onTopicClick = { topicId ->
                navController.navigate(MainScreen.TopicTimeline(topicId))
            }, onSettingsClick = onOpenDrawer)
        }
        composable<MainScreen.TopicTimeline> { backStackEntry ->
            val route: MainScreen.TopicTimeline = backStackEntry.toRoute()
            TimelineScreen(
                topicId = route.topicId,
                onBack = { navController.popBackStack() },
                onSettingsClick = { topicId ->
                    navController.navigate(MainScreen.TopicSettings(topicId))
                })

        }
        composable<MainScreen.Dashboard> {
            ViewScreen()
        }
        composable<MainScreen.TopicSettings> { backStackEntry ->
            val route: MainScreen.TopicSettings = backStackEntry.toRoute()
            TopicSettingsSheet(
                topicId = route.topicId,
                onBack = { navController.popBackStack() },
                onDelete = {
                    navController.popBackStack(MainScreen.Home, inclusive = false)
                })
        }

        composable<MainScreen.Feedback> {
            FeedbackScreen(onBack = { navController.popBackStack() })
        }
        composable<MainScreen.About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}



