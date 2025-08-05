package app.simple.felicity.decorations.singletons

import androidx.recyclerview.widget.RecyclerView

object CarouselScrollStateStore {
    private val scrollPositions = mutableMapOf<String, Int>()

    fun savePosition(id: String, position: Int) {
        scrollPositions[id] = position
    }

    fun RecyclerView.savePosition(id: String) {
        savePosition(id, scrollX)
    }

    fun getPosition(id: String): Int = scrollPositions[id] ?: 0
}