package top.fseasy.imlog.features.appinit

sealed interface AppInitStep {
    data object Loading : AppInitStep
    data object SignInUp : AppInitStep
    data object SelectMediaStorageUri : AppInitStep
    data object CreateFirstTopic : AppInitStep
    data object Welcome : AppInitStep
    data object Finished : AppInitStep
}