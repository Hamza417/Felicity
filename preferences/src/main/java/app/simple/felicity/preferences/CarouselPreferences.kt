package app.simple.felicity.preferences

import androidx.core.content.edit

object CarouselPreferences {

    const val CAMERA_EYE_Y = "camera_eye_y"
    const val Z = "z"

    const val CAMERA_EYE_Y_DEFAULT = 100
    const val Z_DEFAULT = 100

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

    fun setZ(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(Z, value)
            }
    }

    fun getZ(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(Z, Z_DEFAULT)
    }
}