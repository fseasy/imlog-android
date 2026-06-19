package top.fseasy.imlog.features.appinit

import top.fseasy.imlog.domain.model.UserId

sealed interface AppInitStep {
    data object Loading : AppInitStep
    data object SignInUp : AppInitStep
    data class SelectMediaStorageUri(val userId: UserId) : AppInitStep
    data class Welcome(val userId: UserId, val needCreateFirstTopic: Boolean) : AppInitStep
    data object Finished : AppInitStep
}