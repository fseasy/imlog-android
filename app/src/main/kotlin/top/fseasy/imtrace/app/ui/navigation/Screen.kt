package top.fseasy.imtrace.app.ui.navigation

sealed class Screen(val route: String) {
    data object Topics : Screen("topics")
    data object View : Screen("view")
    data object Settings : Screen("settings")
    data object TopicDetail : Screen("topic/{topicId}") {
        fun createRoute(topicId: String) = "topic/$topicId"
    }
    data object TopicSettings : Screen("topic/{topicId}/settings") {
        fun createRoute(topicId: String) = "topic/$topicId/settings"
    }
    data object Feedback : Screen("feedback")
    data object About : Screen("about")
}
