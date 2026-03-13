package app.simple.felicity.decorations.utils

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import app.simple.felicity.preferences.BehaviourPreferences

object VibrateUtils {
    /**
     * Vibrates using the [Vibrator] service with [VibrationAttributes.USAGE_MEDIA] on API 33+
     * so the effect is never silenced by the "Touch vibration" system setting.
     * Falls back to a [VibrationEffect.createPredefined] call (API 29+) without attributes,
     * and ultimately to [performHapticFeedback] on older devices.
     */
    fun Context.vibrateEffect(effectId: Int, tag: String = "VibrateUtils") {
        if (!BehaviourPreferences.isHapticFeedbackEnabled()) {
            Log.d(tag, "Haptic feedback is disabled in preferences, skipping vibration.")
            return
        }

        Log.d(tag, "Attempting to vibrate with effectId: $effectId")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrateWithAttributes(effectId)
        } else {
            // API 29-32: VibrationEffect without attributes — still not gated by touch setting
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()?.vibrate(VibrationEffect.createPredefined(effectId))
        }
    }

    fun Context.vibrate(amplitude: Int, duration: Long = 100L, tag: String = "VibrateUtils") {
        if (!BehaviourPreferences.isHapticFeedbackEnabled()) {
            Log.d(tag, "Haptic feedback is disabled in preferences, skipping vibration.")
            return
        }

        Log.d(tag, "Attempting to vibrate with amplitude: $amplitude, duration: $duration")
        getSystemService<Vibrator>()?.vibrate(
                VibrationEffect.createOneShot(duration, amplitude)
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun Context.vibrateWithAttributes(effectId: Int) {
        if (!BehaviourPreferences.isHapticFeedbackEnabled()) {
            return
        }

        val attrs = VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_MEDIA)
            .build()
        getSystemService<Vibrator>()
            ?.vibrate(VibrationEffect.createPredefined(effectId), attrs)
    }
}