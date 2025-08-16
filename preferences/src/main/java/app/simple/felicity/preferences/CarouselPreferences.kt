package app.simple.felicity.preferences

import androidx.core.content.edit

object CarouselPreferences {

    const val CAMERA_EYE_Y = "camera_eye_y"

    // -------------------------------------------------------------------------------------------- //

    fun setCameraY(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(CAMERA_EYE_Y, value)
            }
    }

    fun getEyeY(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(CAMERA_EYE_Y, 100)
    }

    // -------------------------------------------------------------------------------------------- //
}