package top.fseasy.imlog.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class AppInitData(
    val userId: UserId,
    val storageUriSelected: Boolean,
    val firstTopicCreated: Boolean,
    val WelcomeShown: Boolean,
)