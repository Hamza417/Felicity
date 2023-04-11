package app.simple.felicity.utils

object ArrayUtils {

    /**
     * Convert [MutableList] to [ArrayList]
     */
    fun <T> MutableList<T>.toArrayList(): ArrayList<T> {
        return ArrayList(this)
    }
}