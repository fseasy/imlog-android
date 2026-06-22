package top.fseasy.imlog.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun RootNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = RootScreen.AppInit
    ) {
        // 流程一：初始化流程
        composable<RootScreen.AppInit> {
            AppInitHost(
                onInitFinishedNavigate = {
                    navController.navigate(RootScreen.Main.route) {
                        popUpTo(RootScreen.Init.route) { inclusive = true }
                    }
                }
            )
        }
        // 流程二：主业务流程
        composable<RootScreen.Main> {
            MainNavHost(
                onLogoutNavigate = {
                    // 核心逻辑：清空所有返回栈（popUpTo(0)），并重新跳转到 Init 流程
                    navController.navigate(RootScreen.Init.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            ) // 这里面含有它自己的子 navController
        }
    }
}