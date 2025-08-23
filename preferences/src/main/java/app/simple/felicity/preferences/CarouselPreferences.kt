package app.simple.felicity.preferences

import androidx.core.content.edit

object CarouselPreferences {

    const val CAMERA_EYE_Y = "camera_eye_y"
    const val Z_SPREAD = "z_spread"
    const val REFLECTION_GAP = "reflection_gap"
    const val SCALE = "scale"

    const val CAMERA_EYE_Y_DEFAULT = 0
    const val Z_SPREAD_DEFAULT = 35
    const val REFLECTION_GAP_DEFAULT = 5
    const val REFLECTION_BLUR_DEFAULT = 0
    const val SCALE_DEFAULT = 75

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

    // -------------------------------------------------------------------------------------------- //

    fun setScale(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(SCALE, value)
            }
    }

    fun getScale(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SCALE, SCALE_DEFAULT)
    }
}