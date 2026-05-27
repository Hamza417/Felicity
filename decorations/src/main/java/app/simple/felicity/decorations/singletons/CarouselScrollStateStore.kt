package app.simple.felicity.decorations.singletons

import android.os.Parcelable

/**
 * A simple in-memory store that holds the scroll state of every horizontal carousel
 * by a unique string key. Each entry is a [Parcelable] produced by the layout manager,
 * so both the first visible item position and its pixel offset are preserved.
 *
 * @author Hamza417
 */
object CarouselScrollStateStore {

    private val states = mutableMapOf<String, Parcelable>()

    /**
     * Saves the layout manager state for a carousel identified by [key].
     * Pass the value returned by [LinearLayoutManager.onSaveInstanceState].
     */
    fun saveState(key: String, state: Parcelable) {
        states[key] = state
    }

    /**
     * Returns the previously saved layout manager state for [key], or null
     * if nothing has been saved yet.
     */
    fun getState(key: String): Parcelable? = states[key]

    /** Removes the saved state for [key], if any. */
    fun clearState(key: String) {
        states.remove(key)
    }
}