package app.simple.felicity.preferences

import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.manager.SharedPreferences

object BehaviourPreferences {

    private const val dimWindows = "is_dimming_windows_on"
    private const val blurWindow = "is_blurring_windows_on"
    const val COLORED_SHADOWS = "are_colored_shadows_on"
    private const val transition = "is_transition_on"
    private const val arcAnimation = "is_animation_on"
    private const val marquee = "is_marquee_on"
    private const val skipLoading = "skip_main_loading_screen"
    private const val ANIMATION_DURATION = "animation_duration"

    const val transitionType = "panel_transition_type"
    const val arcType = "arc_type"
    const val stiffness = "scrolling_stiffness"
    const val dampingRatio = "scrolling_damping_ratio"

    // ---------------------------------------------------------------------------------------------------------- //

    fun setDimWindows(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(dimWindows, boolean).apply()
    }

    fun isDimmingOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(dimWindows, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setBlurWindows(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(blurWindow, boolean).apply()
    }

    fun isBlurringOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(blurWindow, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setColoredShadows(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(COLORED_SHADOWS, boolean).apply()
    }

    fun areColoredShadowsOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(COLORED_SHADOWS, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setTransitionOn(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(transition, boolean).apply()
    }

    fun isTransitionOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(transition, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setArcAnimations(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(arcAnimation, boolean).apply()
    }

    fun isArcAnimationOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(arcAnimation, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setMarquee(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(marquee, boolean).apply()
    }

    fun isMarqueeOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(marquee, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setSkipLoadingMainScreenState(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(skipLoading, boolean).apply()
    }

    fun isSkipLoadingMainScreenState(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(skipLoading, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setDampingRatio(value: Float) {
        SharedPreferences.getSharedPreferences().edit().putFloat(dampingRatio, value).apply()
    }

    fun getDampingRatio(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(dampingRatio, SpringForce.DAMPING_RATIO_NO_BOUNCY)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setStiffness(value: Float) {
        SharedPreferences.getSharedPreferences().edit().putFloat(stiffness, value).apply()
    }

    fun getStiffness(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(stiffness, SpringForce.STIFFNESS_LOW)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAnimationDuration(value: Long) {
        SharedPreferences.getSharedPreferences().edit().putLong(ANIMATION_DURATION, value).apply()
    }

    fun getAnimationDuration(): Long {
        return SharedPreferences.getSharedPreferences().getLong(ANIMATION_DURATION, 500)
    }
}
