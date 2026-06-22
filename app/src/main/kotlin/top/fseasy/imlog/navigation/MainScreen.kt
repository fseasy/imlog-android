package top.fseasy.imlog.navigation

import kotlinx.serialization.Serializable
import top.fseasy.imlog.domain.model.TopicId


sealed interface MainScreen {
    @Serializable
    data object Home : MainScreen

    @Serializable
    data class TopicTimeline(val topicId: TopicId) : MainScreen

    @Serializable
    data class TopicSettings(val topicId: TopicId) : MainScreen

    @Serializable
    data object Dashboard : MainScreen

    @Serializable
    data object Settings : MainScreen

    @Serializable
    data object Feedback : MainScreen

    @Serializable
    data object About : MainScreen
}

