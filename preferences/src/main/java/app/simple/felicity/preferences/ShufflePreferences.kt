package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object ShufflePreferences {

    const val SHUFFLE_ALGORITHM = "songs_shuffle_algorithm"

    /** Algorithm constant values — mirror Shuffle.FISHER_YATES and Shuffle.MILLER */
    const val ALGORITHM_FISHER_YATES = 0
    const val ALGORITHM_MILLER = 1

    // --------------------------------------------------------------------------------------------- //

    fun getShuffleAlgorithm(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SHUFFLE_ALGORITHM, ALGORITHM_FISHER_YATES)
    }

    fun setShuffleAlgorithm(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SHUFFLE_ALGORITHM, value)
        }
    }
}
