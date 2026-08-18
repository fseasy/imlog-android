package top.fseasy.imlog.features.appinit

import kotlinx.serialization.Serializable
import top.fseasy.imlog.domain.model.UserId

@Serializable
sealed interface AppInitStep {
  @Serializable data object Auth : AppInitStep

  @Serializable data class SelectMediaStorageUri(val userId: UserId) : AppInitStep

  @Serializable
  data class Welcome(val userId: UserId, val needCreateFirstTopic: Boolean) : AppInitStep

  @Serializable data object Finished : AppInitStep
}
