package top.fseasy.imlog.navigation


import androidx.annotation.StringRes
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.features.appinit.AppInitGraph
import top.fseasy.imlog.features.appinit.appInitGraph
import top.fseasy.imlog.features.settings.SettingsDrawer

private data class BottomNavItem<T : Any>(
    val route: T,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val isSelected: (NavDestination?) -> Boolean,
)

@Composable
fun RootAppScreen(navController: NavHostController = rememberNavController()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val isAtHome = currentDestination?.hasRoute<MainScreen.Home>() == true

    // 3. 使用 remember 缓存底部栏配置，避免每次 Recomposition 都重复创建
    val bottomNavItems = remember {
        listOf(
            BottomNavItem(
                route = MainScreen.Home,
                icon = Icons.Default.Home,
                labelRes = R.string.nav_home,
                isSelected = { dest -> dest?.hasRoute<MainScreen.Home>() == true }), BottomNavItem(
                route = MainScreen.Dashboard,
                icon = Icons.Default.Settings,
                labelRes = R.string.nav_dashboard,
                isSelected = { dest -> dest?.hasRoute<MainScreen.Dashboard>() == true })
        )
    }
    val showBottomBar = isAtHome || bottomNavItems.any { it.isSelected(currentDestination) }

    ModalNavigationDrawer(
        drawerState = drawerState, gesturesEnabled = isAtHome, drawerContent = {
            SettingsDrawer(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route)
                })
        }) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        // 4. 循环生成导航按钮
                        bottomNavItems.forEach { item ->
                            val selected = item.isSelected(currentDestination)
                            NavigationBarItem(selected = selected, onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }, icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            }, label = { Text(stringResource(item.labelRes)) })
                        }
                    }
                }
            }) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = AppInitGraph,
                modifier = Modifier.padding(paddingValues)
            ) {
                appInitGraph(navController, onInitSuccessNavigate = {
                    navController.navigate(MainGraph) {
                        popUpTo(0) { inclusive = true }
                    }
                })
                mainGraph(
                    navController,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSignedOutNavigate = {
                        navController.navigate(AppInitGraph) {
                            popUpTo(0) { inclusive = true }
                        }
                    })
            }
        }
    }
}