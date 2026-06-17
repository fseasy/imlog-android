package top.fseasy.imlog.domain.repository

@JvmInline
value class StringArrayResId(val value: Int)

interface ResourceProvider {
    fun getStringArray(id: StringArrayResId): Array<String>
}