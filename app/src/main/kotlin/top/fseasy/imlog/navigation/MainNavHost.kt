package top.fseasy.imlog.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
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
import top.fseasy.imlog.features.settings.FeedbackScreen
import top.fseasy.imlog.features.settings.AboutScreen
import top.fseasy.imlog.features.view.ViewScreen
import top.fseasy.imlog.features.log.ui.TopicsRoute
import top.fseasy.imlog.features.log.ui.TimelineScreen
import top.fseasy.imlog.features.log.ui.TopicSettingsSheet
import top.fseasy.imlog.features.settings.SettingsDrawer
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.features.appinit.MainViewModel

@Composable
fun MainNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(MainScreen.Home.route, MainScreen.Dashboard.route)

    ModalNavigationDrawer(
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
                            selected = currentRoute == MainScreen.Home.route,
                            onClick = {
                                if (currentRoute != MainScreen.Home.route) {
                                    navController.navigate(MainScreen.Home.route) {
                                        popUpTo(MainScreen.Home.route) { inclusive = true }
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "View") },
                            label = { Text("View") },
                            selected = currentRoute == MainScreen.Dashboard.route,
                            onClick = {
                                if (currentRoute != MainScreen.Dashboard.route) {
                                    navController.navigate(MainScreen.Dashboard.route) {
                                        popUpTo(MainScreen.Home.route)
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
                startDestination = MainScreen.Home.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(MainScreen.Home.route) {
                    TopicsRoute(
                        onTopicClick = { topicId ->
                            navController.navigate(MainScreen.TopicTimeline.createRoute(topicId))
                        },
                        onSettingsClick = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }
                composable(MainScreen.Dashboard.route) {
                    ViewScreen()
                }
                composable(
                    route = MainScreen.TopicTimeline.route,
                    arguments = listOf(navArgument("topicId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val topicId = backStackEntry.arguments?.getString("topicId")
                        ?.let(::TopicId) ?: return@composable
                    TimelineScreen(
                        topicId = topicId,
                        onBack = { navController.popBackStack() },
                        onSettingsClick = { topicId ->
                            navController.navigate(MainScreen.TopicSettings.createRoute(topicId))
                        }
                    )
                }
                composable(
                    route = MainScreen.TopicSettings.route,
                    arguments = listOf(navArgument("topicId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val topicId =
                        backStackEntry.arguments?.getString("topicId") ?: return@composable
                    TopicSettingsSheet(
                        topicId = topicId,
                        onBack = { navController.popBackStack() },
                        onDelete = {
                            navController.popBackStack(MainScreen.Home.route, inclusive = false)
                        }
                    )
                }
                composable(MainScreen.Feedback.route) {
                    FeedbackScreen(onBack = { navController.popBackStack() })
                }
                composable(MainScreen.About.route) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
