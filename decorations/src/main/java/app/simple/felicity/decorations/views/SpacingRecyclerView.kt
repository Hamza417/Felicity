package app.simple.felicity.decorations.views

import android.content.SharedPreferences
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.decorations.overscroll.CustomVerticalRecyclerView
import app.simple.felicity.preferences.AppearancePreferences

class SpacingRecyclerView : CustomVerticalRecyclerView {

    constructor(context: android.content.Context) : super(context)
    constructor(context: android.content.Context, attrs: android.util.AttributeSet?) : super(context, attrs)
    constructor(context: android.content.Context, attrs: android.util.AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        updateSpacing()
    }

    fun updateSpacing() {
        for (i in 0 until itemDecorationCount) {
            val decoration = getItemDecorationAt(i)
            if (decoration is SpacingItemDecoration) {
                removeItemDecoration(decoration)
                break
            }
        }

        addItemDecoration(
                SpacingItemDecoration(
                        AppearancePreferences.DEFAULT_SPACING.toInt(),
                        AppearancePreferences.getListSpacing().toInt()))
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AppearancePreferences.LIST_SPACING -> {
                updateSpacing()
            }
        }
    }
}