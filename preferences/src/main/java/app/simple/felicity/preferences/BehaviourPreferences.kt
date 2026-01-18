package app.simple.felicity.preferences

import androidx.core.content.edit
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.manager.SharedPreferences

object BehaviourPreferences {

    const val PREDICTIVE_BACK = "predictive_back"
    const val STIFFNESS = "scrolling_stiffness"
    const val DAMPING_RATIO = "scrolling_damping_ratio"
    private const val MARQUEE = "is_marquee_on"
    private const val FAST_SCROLL_BEHAVIOR = "fast_scroll_behavior"

    const val HIDE_FAST_SCROLLBAR = 0
    const val FADE_FAST_SCROLLBAR = 1

    // ---------------------------------------------------------------------------------------------------------- //

    fun setPredictiveBack(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(PREDICTIVE_BACK, enabled) }
    }

    fun isPredictiveBackEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(PREDICTIVE_BACK, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setDampingRatio(value: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(DAMPING_RATIO, value) }
    }

    fun getDampingRatio(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(DAMPING_RATIO, SpringForce.DAMPING_RATIO_NO_BOUNCY)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setStiffness(value: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(STIFFNESS, value) }
    }

    fun getStiffness(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(STIFFNESS, SpringForce.STIFFNESS_LOW)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setMarquee(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(MARQUEE, boolean)
        }
    }

    fun isMarqueeOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(MARQUEE, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setFastScrollBehavior(behavior: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(FAST_SCROLL_BEHAVIOR, behavior)
        }
    }

    fun getFastScrollBehavior(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(FAST_SCROLL_BEHAVIOR, FADE_FAST_SCROLLBAR)
    }
}
