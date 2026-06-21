package top.fseasy.imlog.data.repository

import android.content.Context
import top.fseasy.imlog.R
import top.fseasy.imlog.data.constants.APP_STATIC_NAME
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringArrayResId
import top.fseasy.imlog.domain.repository.StringConstantId
import top.fseasy.imlog.domain.repository.StringResId
import javax.inject.Inject

class ResourceProviderImpl @Inject constructor(
    private val context: Context,
) : ResourceProvider {
    override fun getStringArray(id: StringArrayResId): Array<String> {
        return context.resources.getStringArray(
            when (id) {
                StringArrayResId.RandomUsernameAdjectives -> R.array.random_username_adjectives
                StringArrayResId.RandomUsernameNouns -> R.array.random_username_nouns
            }
        )
    }

    override fun getString(id: StringResId): String {
        return context.resources.getString(
            when (id) {
                StringResId.AppName -> R.string.app_name
                StringResId.TopicInitialName -> R.string.topic_initial_name
                StringResId.TopicInitialDescription -> R.string.topic_initial_description
            }
        )
    }

    override fun getConstString(id: StringConstantId): String {
        return when (id) {
            StringConstantId.AppStaticName -> APP_STATIC_NAME
        }
    }


}