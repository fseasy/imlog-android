package top.fseasy.imlog.ui.theme

import androidx.annotation.DrawableRes
import top.fseasy.imlog.R

enum class PresetAvatar(@DrawableRes val resId: Int, val dbName: String) {
    RABBIT(R.drawable.rabbit, "rabbit"),
    PANDA(R.drawable.panda, "panda"),
    FOX(R.drawable.fox, "fox");

    companion object {
        private val lookupMap by lazy { entries.associateBy { it.dbName } }
        fun random(): PresetAvatar = entries.random()
        fun first(): PresetAvatar = entries.first()
        fun fromDbNameOrRandom(dbName: String): PresetAvatar {
            return lookupMap[dbName] ?: random()
        }
    }
}

