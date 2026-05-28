package top.fseasy.imtrace.app.ui

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
import androidx.compose.runtime.collectAsState
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
import top.fseasy.imtrace.app.ui.navigation.Screen
import top.fseasy.imtrace.app.ui.settings.FeedbackScreen
import top.fseasy.imtrace.app.ui.settings.AboutScreen
import top.fseasy.imtrace.app.ui.view.ViewScreen
import top.fseasy.imtrace.app.ui.topics.TopicsScreen
import top.fseasy.imtrace.app.ui.topics.TopicDetailScreen
import top.fseasy.imtrace.app.ui.topics.TopicSettingsSheet
import top.fseasy.imtrace.app.ui.settings.SettingsDrawer
import kotlinx.coroutines.launch

@Composable
fun ImtraceApp(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(Screen.Topics.route, Screen.View.route)

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
                            selected = currentRoute == Screen.Topics.route,
                            onClick = {
                                if (currentRoute != Screen.Topics.route) {
                                    navController.navigate(Screen.Topics.route) {
                                        popUpTo(Screen.Topics.route) { inclusive = true }
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "View") },
                            label = { Text("View") },
                            selected = currentRoute == Screen.View.route,
                            onClick = {
                                if (currentRoute != Screen.View.route) {
                                    navController.navigate(Screen.View.route) {
                                        popUpTo(Screen.Topics.route)
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
                startDestination = Screen.Topics.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.Topics.route) {
                    TopicsScreen(
                        onTopicClick = { topicId ->
                            navController.navigate(Screen.TopicDetail.createRoute(topicId))
                        },
                        onSettingsClick = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }
                composable(Screen.View.route) {
                    ViewScreen()
                }
                composable(
                    route = Screen.TopicDetail.route,
                    arguments = listOf(navArgument("topicId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val topicId = backStackEntry.arguments?.getString("topicId") ?: return@composable
                    TopicDetailScreen(
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
                    val topicId = backStackEntry.arguments?.getString("topicId") ?: return@composable
                    TopicSettingsSheet(
                        topicId = topicId,
                        onBack = { navController.popBackStack() },
                        onDelete = {
                            navController.popBackStack(Screen.Topics.route, inclusive = false)
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
