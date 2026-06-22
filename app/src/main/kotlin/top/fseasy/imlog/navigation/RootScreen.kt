package top.fseasy.imlog.navigation

import kotlinx.serialization.Serializable

sealed interface RootScreen {
    @Serializable
    data object AppInit : RootScreen

    @Serializable
    data object Main : RootScreen
}