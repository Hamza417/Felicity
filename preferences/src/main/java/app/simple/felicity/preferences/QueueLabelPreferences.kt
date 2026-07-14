package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.preferences.QueueLabelPreferences.getLabel

/**
 * Stores and retrieves custom user-assigned labels for each playback queue slot.
 *
 * Labels live in [SharedPreferences] under keys like `queue_label_0`,
 * `queue_label_1`, etc.
 *
 * When a slot has no custom label, [getLabel] returns `null` and callers
 * should fall back to the default "Queue N" string.
 *
 * @author Hamza417
 */
object QueueLabelPreferences {

    /**
     * Prefix used for every queue-label key in SharedPreferences.
     * The full key is formed by appending the queue index (e.g. "queue_label_3").
     */
    private const val KEY_PREFIX = "queue_label_"

    /**
     * Returns the custom label for [queueId], or `null` when the user hasn't
     * set one yet.
     *
     * @param queueId zero-based queue slot index (0 .. QUEUE_COUNT-1).
     */
    fun getLabel(queueId: Int): String? {
        return SharedPreferences.getSharedPreferences()
            .getString(KEY_PREFIX + queueId, null)
    }

    /**
     * Persists a custom label for [queueId].
     *
     * Pass `null` or a blank string to erase the label and revert to the
     * system default.
     *
     * @param queueId zero-based queue slot index.
     * @param label   the human-readable name to show, or null/blank to reset.
     */
    fun setLabel(queueId: Int, label: String?) {
        SharedPreferences.getSharedPreferences().edit {
            val key = KEY_PREFIX + queueId
            if (label.isNullOrBlank()) {
                remove(key)
            } else {
                putString(key, label)
            }
        }
    }

    /**
     * Collects every custom label that has been saved, keyed by slot index.
     *
     * Only slots whose labels differ from the default are included in the
     * returned map. Use this when you need to display custom names in a
     * picker or summary view.
     */
    fun getAllLabels(count: Int): Map<Int, String> {
        val prefs = SharedPreferences.getSharedPreferences()
        val result = mutableMapOf<Int, String>()
        for (i in 0 until count) {
            prefs.getString(KEY_PREFIX + i, null)?.let { label ->
                result[i] = label
            }
        }
        return result
    }

    /**
     * Builds a human-readable display name for [queueId].
     *
     * When a custom label exists it is returned as-is; otherwise a fallback
     * string in the form `"Queue N"` (1-based) is produced.
     *
     * @param queueId      zero-based queue slot index.
     * @param defaultLabel a lambda that receives the 1-based queue number
     *                     and returns the fallback string (typically from
     *                     a string resource).
     */
    fun getDisplayLabel(queueId: Int, defaultLabel: (Int) -> String): String {
        return getLabel(queueId) ?: defaultLabel(queueId + 1)
    }
}
