package top.fseasy.imlog.features.appinit

import top.fseasy.imlog.domain.model.UserId

sealed interface AppInitStep {
    data object Auth : AppInitStep
    data class SelectMediaStorageUri(val userId: UserId) : AppInitStep
    data class Welcome(val userId: UserId, val needCreateFirstTopic: Boolean) : AppInitStep
    data object Finished : AppInitStep
}