package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object CarouselPreferences {

    const val CAMERA_EYE_Y = "camera_eye_y_"
    const val Z_SPREAD = "z_spread_"
    const val REFLECTION_GAP = "reflection_gap_"
    const val SCALE = "scale_"

    const val CAMERA_EYE_Y_DEFAULT = 0F
    const val Z_SPREAD_DEFAULT = 0.35F
    const val REFLECTION_GAP_DEFAULT = 0.05F
    const val SCALE_DEFAULT = 0.75F

    // -------------------------------------------------------------------------------------------- //

    fun setEyeY(value: Float) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putFloat(CAMERA_EYE_Y, value)
            }
    }

    fun getEyeY(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(CAMERA_EYE_Y, CAMERA_EYE_Y_DEFAULT)
    }

    // -------------------------------------------------------------------------------------------- //

    fun setZSpread(value: Float) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putFloat(Z_SPREAD, value)
            }
    }

    fun getZSpread(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(Z_SPREAD, Z_SPREAD_DEFAULT)
    }

    // -------------------------------------------------------------------------------------------- //

    fun setReflectionGap(value: Float) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putFloat(REFLECTION_GAP, value)
            }
    }

    fun getReflectionGap(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(REFLECTION_GAP, REFLECTION_GAP_DEFAULT)
    }

    // -------------------------------------------------------------------------------------------- //

    fun setScale(value: Float) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putFloat(SCALE, value)
            }
    }

    fun getScale(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(SCALE, SCALE_DEFAULT)
    }
}