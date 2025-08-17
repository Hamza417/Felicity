package app.simple.felicity.theme.managers

import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme

object ThemeManager {
    private val listeners = mutableSetOf<ThemeChangedListener>()

    var theme: Theme = Theme()
        set(value) {
            val bool = field != value
            field = value
            listeners.forEach { listener -> listener.onThemeChanged(value, bool) }
        }

    var accent = Accent(0, 0)
        set(value) {
            field = value
            listeners.forEach { it.onAccentChanged(accent) }
        }

    fun addListener(listener: ThemeChangedListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ThemeChangedListener) {
        listeners.remove(listener)
    }
}
