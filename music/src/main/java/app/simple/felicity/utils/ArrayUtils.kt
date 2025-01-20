package app.simple.felicity.utils

object ArrayUtils {

    /**
     * Convert [MutableList] to [ArrayList]
     */
    fun <T> MutableList<T>.toArrayList(): ArrayList<T> {
        return ArrayList(this)
    }

    /**
     * Swap two elements in the array
     */
    fun <T> ArrayList<T>.swap(index1: Int, index2: Int) {
        val temp = this[index1]
        this[index1] = this[index2]
        this[index2] = temp
    }

    fun IntArray.getTwoRandomIndices(): Set<Int> {
        val set = mutableSetOf<Int>()
        while (set.size < 2) {
            set.add((indices).random())
        }
        return set
    }
}