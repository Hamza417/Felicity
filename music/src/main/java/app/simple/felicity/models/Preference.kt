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

    var valueProvider: Supplier<String?>? = null

    var onClickAction: ((View) -> Any?)? = null

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

    val value: String?
        get() = if (valueProvider != null) valueProvider!!.get() else ""
}


