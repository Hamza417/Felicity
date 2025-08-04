package app.simple.felicity.theme.themes.dark

import app.simple.felicity.theme.data.MaterialYou
import app.simple.felicity.theme.models.IconTheme
import app.simple.felicity.theme.models.SwitchTheme
import app.simple.felicity.theme.models.TextViewTheme
import app.simple.felicity.theme.models.ViewGroupTheme
import app.simple.felicity.theme.themes.Theme

class MaterialYouDark : Theme() {
    init {
        setTextViewTheme(TextViewTheme(
                MaterialYou.headingTextColorDark,
                MaterialYou.primaryTextColorDark,
                MaterialYou.secondaryTextColorDark,
                MaterialYou.tertiaryTextColorDark,
                MaterialYou.quaternaryTextColorDark
        ))

        setViewGroupTheme(ViewGroupTheme(
                MaterialYou.backgroundDark,
                MaterialYou.highlightBackgroundDark,
                MaterialYou.selectedBackgroundDark,
                MaterialYou.dividerBackgroundDark,
                MaterialYou.spotColorDark,
        ))

        setSwitchTheme(SwitchTheme(
                MaterialYou.switchOffColorDark
        ))

        setIconTheme(IconTheme(
                MaterialYou.regularIconColorDark,
                MaterialYou.secondaryIconColorDark,
                MaterialYou.highlightBackgroundDark // fallback for disabled icon color
        ))
    }
}