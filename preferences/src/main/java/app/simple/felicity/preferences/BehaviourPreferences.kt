package app.simple.felicity.preferences

import android.os.Build
import androidx.core.content.edit
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.preferences.BehaviourPreferences.TEXT_EFFECT_FADE
import app.simple.felicity.preferences.BehaviourPreferences.TEXT_EFFECT_NONE
import app.simple.felicity.preferences.BehaviourPreferences.TEXT_EFFECT_SLIDE
import app.simple.felicity.preferences.BehaviourPreferences.TEXT_EFFECT_TYPEWRITING

object BehaviourPreferences {

    const val PREDICTIVE_BACK = "predictive_back"
    const val STIFFNESS = "scrolling_stiffness"
    const val DAMPING_RATIO = "scrolling_damping_ratio"
    private const val MARQUEE = "is_marquee_on"
    private const val FAST_SCROLL_BEHAVIOR = "fast_scroll_behavior"
    private const val HAPTIC_FEEDBACK = "haptic_feedback"
    const val MINIPLAYER_ALWAYS_VISIBLE = "miniplayer_always_visible"
    const val TEXT_CHANGE_EFFECT = "text_change_effect"

    const val HIDE_FAST_SCROLLBAR = 0
    const val FADE_FAST_SCROLLBAR = 1

    /** Text change animation: no animation, text is set instantly. */
    const val TEXT_EFFECT_NONE = 0

    /** Text change animation: cross-fade (fade out old text, then fade in new text). */
    const val TEXT_EFFECT_FADE = 1

    /** Text change animation: slide out in song-change direction, change text, slide back in. */
    const val TEXT_EFFECT_SLIDE = 2

    /** Text change animation: typewriter character-by-character reveal. */
    const val TEXT_EFFECT_TYPEWRITING = 3

    // ---------------------------------------------------------------------------------------------------------- //

    fun setPredictiveBack(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(PREDICTIVE_BACK, enabled) }
    }

    fun isPredictiveBackEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SharedPreferences.getSharedPreferences().getBoolean(PREDICTIVE_BACK, true)
        } else {
            false
        }
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

    // ---------------------------------------------------------------------------------------------------------- //

    fun setHapticFeedback(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(HAPTIC_FEEDBACK, enabled)
        }
    }

    fun isHapticFeedbackEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences()
            .getBoolean(HAPTIC_FEEDBACK, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setMiniplayerAlwaysVisible(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(MINIPLAYER_ALWAYS_VISIBLE, enabled)
        }
    }

    fun isMiniplayerAlwaysVisible(): Boolean {
        return SharedPreferences.getSharedPreferences()
            .getBoolean(MINIPLAYER_ALWAYS_VISIBLE, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    /**
     * Persists the text change animation style used by the player screen
     * when the song title, artist, and album labels update on a track change.
     *
     * @param effect One of [TEXT_EFFECT_NONE], [TEXT_EFFECT_FADE], [TEXT_EFFECT_SLIDE],
     *               or [TEXT_EFFECT_TYPEWRITING].
     */
    fun setTextChangeEffect(effect: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(TEXT_CHANGE_EFFECT, effect)
        }
    }

    /**
     * Returns the currently saved text change animation style.
     * Defaults to [TEXT_EFFECT_TYPEWRITING] when the preference has not been set.
     */
    fun getTextChangeEffect(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(TEXT_CHANGE_EFFECT, TEXT_EFFECT_TYPEWRITING)
    }
}
