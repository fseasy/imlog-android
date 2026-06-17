package top.fseasy.imlog.data.repository

import android.content.Context
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringArrayResId
import javax.inject.Inject

class ResourceProviderImpl @Inject constructor(
    private val context: Context,
) : ResourceProvider {
    override fun getStringArray(id: StringArrayResId): Array<String> {
        return context.resources.getStringArray(id.value)
    }
}