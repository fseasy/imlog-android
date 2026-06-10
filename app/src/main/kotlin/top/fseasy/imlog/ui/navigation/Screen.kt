package top.fseasy.imlog.ui.navigation

import top.fseasy.imlog.domain.model.TopicId

sealed class Screen(val route: String) {
    data object Log : Screen("log")
    data object LogTimeline : Screen("log_timeline/{topicId}") {
        // Must explicitly use .value. the default toString result: TopicId(value=xxx)
        fun createRoute(topicId: TopicId) = "log_timeline/${topicId.value}"
    }

    data object TopicSettings : Screen("topic_settings/{topicId}") {
        fun createRoute(topicId: TopicId) = "topic_settings/${topicId.value}"
    }

    data object Dashboard : Screen("dashboard")
    data object Settings : Screen("settings")
    data object Feedback : Screen("feedback")
    data object About : Screen("about")
}
