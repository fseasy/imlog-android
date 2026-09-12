package top.fseasy.imlog.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import top.fseasy.imlog.features.appinit.AppInitGraph
import top.fseasy.imlog.features.appinit.appInitGraph

@Composable
fun RootAppScreen(navController: NavHostController = rememberNavController()) {
  val fastFadeSpec = tween<Float>(durationMillis = 150, easing = LinearEasing)

  NavHost(
      navController = navController,
      startDestination = AppInitGraph,
      enterTransition = { fadeIn(animationSpec = fastFadeSpec) },
      exitTransition = { fadeOut(animationSpec = fastFadeSpec) },
      popEnterTransition = { fadeIn(animationSpec = fastFadeSpec) },
      popExitTransition = { fadeOut(animationSpec = fastFadeSpec) },
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
