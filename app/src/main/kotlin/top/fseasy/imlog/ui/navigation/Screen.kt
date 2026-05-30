package top.fseasy.imlog.ui.navigation

sealed class Screen(val route: String) {
    data object Log : Screen("log")
    data object LogTimeline : Screen("log_timeline/{topicId}") {
        fun createRoute(topicId: String) = "log_timeline/$topicId"
    }
    data object TopicSettings : Screen("topic_settings/{topicId}") {
        fun createRoute(topicId: String) = "topic_settings/$topicId"
    }
    data object Dashboard : Screen("dashboard")
    data object Settings : Screen("settings")
    data object Feedback : Screen("feedback")
    data object About : Screen("about")
}
