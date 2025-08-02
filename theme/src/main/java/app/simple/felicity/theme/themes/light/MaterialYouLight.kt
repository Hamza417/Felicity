package app.simple.felicity.theme.themes.light

import app.simple.felicity.theme.data.MaterialYou
import app.simple.felicity.theme.models.IconTheme
import app.simple.felicity.theme.models.SwitchTheme
import app.simple.felicity.theme.models.TextViewTheme
import app.simple.felicity.theme.models.ViewGroupTheme
import app.simple.felicity.theme.themes.Theme

class MaterialYouLight : Theme() {
    init {
        textViewTheme = TextViewTheme(
                MaterialYou.headingTextColor,
                MaterialYou.primaryTextColor,
                MaterialYou.secondaryTextColor,
                MaterialYou.tertiaryTextColor,
                MaterialYou.quaternaryTextColor
        )

        viewGroupTheme = ViewGroupTheme(
                MaterialYou.background,
                MaterialYou.highlightBackground,
                MaterialYou.selectedBackground,
                MaterialYou.dividerBackground
        )

        switchTheme = SwitchTheme(
                MaterialYou.switchOffColor
        )

        iconTheme = IconTheme(
                MaterialYou.regularIconColor,
                MaterialYou.secondaryIconColor,
                MaterialYou.highlightBackground // fallback for disabled icon color
        )
    }
}