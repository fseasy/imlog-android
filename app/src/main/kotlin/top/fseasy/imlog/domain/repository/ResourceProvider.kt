package top.fseasy.imlog.domain.repository


enum class StringArrayResId {
    RandomUsernameAdjectives,
    RandomUsernameNouns;
}

enum class StringResId {
    AppName,
    TopicInitialName,
    TopicInitialDescription
}

enum class StringConstantId {
    AppStaticName;
}

interface ResourceProvider {
    fun getStringArray(id: StringArrayResId): Array<String>
    fun getString(id: StringResId): String
    fun getConstString(id: StringConstantId): String
}