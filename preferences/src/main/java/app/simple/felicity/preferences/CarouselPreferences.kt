package app.simple.felicity.preferences

import androidx.core.content.edit

object CarouselPreferences {

    const val CAMERA_EYE_Y = "camera_eye_y"
    const val Z_SPREAD = "z_spread"
    const val REFLECTION_GAP = "reflection_gap"

    const val CAMERA_EYE_Y_DEFAULT = 100
    const val Z_SPREAD_DEFAULT = 35
    const val REFLECTION_GAP_DEFAULT = 5

    // -------------------------------------------------------------------------------------------- //

    fun setEyeY(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(CAMERA_EYE_Y, value)
            }
    }

    fun getEyeY(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(CAMERA_EYE_Y, CAMERA_EYE_Y_DEFAULT)
    }

    // -------------------------------------------------------------------------------------------- //

    fun setZSpread(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(Z_SPREAD, value)
            }
    }

    fun getZSpread(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(Z_SPREAD, Z_SPREAD_DEFAULT)
    }

    // -------------------------------------------------------------------------------------------- //

    fun setReflectionGap(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(REFLECTION_GAP, value)
            }
    }

    fun getReflectionGap(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(REFLECTION_GAP, REFLECTION_GAP_DEFAULT)
    }
}