package app.simple.felicity.core.singletons

object AppOrientation {
    private var isLandscape = false

    fun setOrientation(landscape: Boolean) {
        isLandscape = landscape
    }

    fun isLandscape(): Boolean {
        return isLandscape
    }
}