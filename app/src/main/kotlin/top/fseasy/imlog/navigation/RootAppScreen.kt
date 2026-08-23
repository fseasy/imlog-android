package top.fseasy.imlog.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import top.fseasy.imlog.features.appinit.AppInitGraph
import top.fseasy.imlog.features.appinit.appInitGraph

private data class BottomNavItem<T : Any>(
    val route: T,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val isSelected: (NavDestination?) -> Boolean,
)

@Composable
fun RootAppScreen(navController: NavHostController = rememberNavController()) {
  NavHost(
      navController = navController,
      startDestination = AppInitGraph,
  ) {
    appInitGraph(
        navController,
        onInitSuccessNavigate = {
          navController.navigate(MainGraph) {
            popUpTo(0) { inclusive = true }
          }
        },
    )
    mainGraph(
        navController,
        onSignedOutNavigate = {
          navController.navigate(AppInitGraph) {
            popUpTo(0) { inclusive = true }
          }
        },
    )
  }
}
