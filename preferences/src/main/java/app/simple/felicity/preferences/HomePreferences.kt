package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.manager.SharedPreferences

object HomePreferences {

    const val HOME_INTERFACE = "home_interface_"
    const val HOME_INTERFACE_SPANNED = 2
    const val HOME_INTERFACE_ARTFLOW = 3
    const val HOME_INTERFACE_SIMPLE = 0

    // Home layout type (list / grid)
    const val HOME_LAYOUT_TYPE = "home_layout_type"

    // Persisted order of home items (comma-separated titleResId values)
    const val HOME_ITEMS_ORDER = "home_items_order"

    // ---------------------------------------------------------------------------------------- //

    fun getHomeInterface(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(HOME_INTERFACE, HOME_INTERFACE_SIMPLE)
    }

    fun setHomeInterface(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(HOME_INTERFACE, value)
            }
    }

    fun getHomeLayoutType(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(HOME_LAYOUT_TYPE, CommonPreferencesConstants.GRID_TYPE_LIST)
    }

    fun setHomeLayoutType(type: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(HOME_LAYOUT_TYPE, type)
        }
    }

    fun getHomeItemsOrder(): String {
        return SharedPreferences.getSharedPreferences()
            .getString(HOME_ITEMS_ORDER, "") ?: ""
    }

    fun setHomeItemsOrder(order: String) {
        SharedPreferences.getSharedPreferences().edit {
            putString(HOME_ITEMS_ORDER, order)
        }
    }

    fun resetHomeOrder() {
        SharedPreferences.getSharedPreferences().edit {
            putString(HOME_ITEMS_ORDER, "")
        }

        setHomeLayoutType(CommonPreferencesConstants.GRID_TYPE_LIST)
    }
}