package top.fseasy.imlog.domain.model


data class AppInitData(
    val userId: UserId,
    val storageUriSelected: Boolean,
    val firstTopicCreated: Boolean,
    val WelcomeShown: Boolean,
)