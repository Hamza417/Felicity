package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.manager.SharedPreferences

object HomePreferences {

    // Home layout type (list / grid)
    const val HOME_LAYOUT_TYPE = "home_layout_type"

    // ---------------------------------------------------------------------------------------- //

    fun getHomeLayoutType(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(HOME_LAYOUT_TYPE, CommonPreferencesConstants.GRID_TYPE_LIST)
    }

    fun setHomeLayoutType(type: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(HOME_LAYOUT_TYPE, type)
        }
    }
}