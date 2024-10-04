package app.simple.felicity.theme.themes;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;

public class Theme {
    private static TextViewTheme textViewTheme;
    private static ViewGroupTheme viewGroupTheme;
    private static IconTheme iconTheme;
    private static SwitchTheme switchTheme;

    public Theme() {

    }

    public TextViewTheme getTextViewTheme() {
        return textViewTheme;
    }

    public void setTextViewTheme(TextViewTheme textViewTheme) {
        Theme.textViewTheme = textViewTheme;
    }

    public ViewGroupTheme getViewGroupTheme() {
        return viewGroupTheme;
    }

    public void setViewGroupTheme(ViewGroupTheme viewGroupTheme) {
        Theme.viewGroupTheme = viewGroupTheme;
    }

    public IconTheme getIconTheme() {
        return iconTheme;
    }

    public void setIconTheme(IconTheme iconTheme) {
        Theme.iconTheme = iconTheme;
    }

    public SwitchTheme getSwitchTheme() {
        return switchTheme;
    }

    public void setSwitchTheme(SwitchTheme switchTheme) {
        Theme.switchTheme = switchTheme;
    }
}
