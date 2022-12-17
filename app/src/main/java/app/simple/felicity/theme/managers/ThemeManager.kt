package app.simple.felicity.theme.managers

import app.simple.felicity.interfaces.ThemeChangedListener
import app.simple.felicity.models.AccentColor
import app.simple.felicity.theme.themes.Theme

object ThemeManager {
    private val listeners = mutableSetOf<ThemeChangedListener>()

    var theme = Theme()
        set(value) {
            val bool = field != value
            field = value
            listeners.forEach { listener -> listener.onThemeChanged(value, bool) }
        }

    var accentColor = AccentColor()
        set(value) {
            field = value
            listeners.forEach { it.onAccentChanged(accentColor) }
        }

    fun addListener(listener: ThemeChangedListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ThemeChangedListener) {
        listeners.remove(listener)
    }
}