package top.fseasy.imlog.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import top.fseasy.imlog.features.appinit.AppInitGraph
import top.fseasy.imlog.features.appinit.appInitGraph

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
            popUpTo<AppInitGraph> { inclusive = true }
            launchSingleTop = true
          }
        },
    )
    mainGraph(
        navController,
        onSignedOutNavigate = {
          navController.navigate(AppInitGraph) {
            popUpTo<MainGraph> { inclusive = true }
            launchSingleTop = true
          }
        },
    )
  }
}
