package app.simple.felicity.models

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.simple.felicity.enums.PreferenceType
import java.util.function.Supplier

class Preference {
    @StringRes
    var title: Int

    @StringRes
    var summary: Int = 0

    @DrawableRes
    var icon: Int = 0
    var type: PreferenceType?

    var valueProvider: Supplier<Any?>? = null

    var onPreferenceAction: ((View, (Any?) -> Unit) -> Unit)? = null

    constructor(@StringRes title: Int, @StringRes summary: Int, @DrawableRes icon: Int, type: PreferenceType?) {
        this.title = title
        this.summary = summary
        this.icon = icon
        this.type = type
    }

    constructor(@StringRes title: Int, type: PreferenceType?) {
        this.title = title
        this.type = type
    }

    constructor(@StringRes title: Int,
                @StringRes summary: Int,
                @DrawableRes icon: Int,
                type: PreferenceType?,
                valueProvider: Supplier<Any?>?,
                onPreferenceAction: ((View, (Any?) -> Unit) -> Unit)?) {
        this.title = title
        this.summary = summary
        this.icon = icon
        this.type = type
        this.valueProvider = valueProvider
        this.onPreferenceAction = onPreferenceAction
    }

    val valueAsStringProvider: String?
        get() = valueProvider?.get() as String?

    val valueAsSeekbarStateProvider: SeekbarState?
        get() = valueProvider?.get() as SeekbarState?

    val valueAsBooleanProvider: Boolean?
        get() = valueProvider?.get() as Boolean?
}


