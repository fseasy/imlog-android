package top.fseasy.imlog.features.appinit

sealed interface AppInitState {
    data object Loading : AppInitState
    data object SignInUp : AppInitState
    data object SelectMediaStorageUri : AppInitState
    data object CreateFirstTopic : AppInitState
    data object Welcome : AppInitState
    data object Finished : AppInitState
}