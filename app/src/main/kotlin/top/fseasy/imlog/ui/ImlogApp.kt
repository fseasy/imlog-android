package top.fseasy.imlog.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import top.fseasy.imlog.ui.navigation.Screen
import top.fseasy.imlog.features.settings.FeedbackScreen
import top.fseasy.imlog.features.settings.AboutScreen
import top.fseasy.imlog.features.view.ViewScreen
import top.fseasy.imlog.features.log.ui.TopicsRoute
import top.fseasy.imlog.features.log.ui.TimelineScreen
import top.fseasy.imlog.features.log.ui.TopicSettingsSheet
import top.fseasy.imlog.features.settings.SettingsDrawer
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.TopicId

@Composable
fun ImlogApp(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(Screen.Log.route, Screen.Dashboard.route)

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route)
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Records") },
                            label = { Text("Records") },
                            selected = currentRoute == Screen.Log.route,
                            onClick = {
                                if (currentRoute != Screen.Log.route) {
                                    navController.navigate(Screen.Log.route) {
                                        popUpTo(Screen.Log.route) { inclusive = true }
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "View") },
                            label = { Text("View") },
                            selected = currentRoute == Screen.Dashboard.route,
                            onClick = {
                                if (currentRoute != Screen.Dashboard.route) {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Log.route)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Log.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.Log.route) {
                    TopicsRoute(
                        onTopicClick = { topicId ->
                            navController.navigate(Screen.LogTimeline.createRoute(topicId))
                        },
                        onSettingsClick = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }
                composable(Screen.Dashboard.route) {
                    ViewScreen()
                }
                composable(
                    route = Screen.LogTimeline.route,
                    arguments = listOf(navArgument("topicId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val topicId = backStackEntry.arguments?.getString("topicId")
                        ?.let(::TopicId) ?: return@composable
                    TimelineScreen(
                        topicId = topicId,
                        onBack = { navController.popBackStack() },
                        onSettingsClick = { topicId ->
                            navController.navigate(Screen.TopicSettings.createRoute(topicId))
                        }
                    )
                }
                composable(
                    route = Screen.TopicSettings.route,
                    arguments = listOf(navArgument("topicId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val topicId =
                        backStackEntry.arguments?.getString("topicId") ?: return@composable
                    TopicSettingsSheet(
                        topicId = topicId,
                        onBack = { navController.popBackStack() },
                        onDelete = {
                            navController.popBackStack(Screen.Log.route, inclusive = false)
                        }
                    )
                }
                composable(Screen.Feedback.route) {
                    FeedbackScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.About.route) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
