package app.simple.felicity.theme.themes;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;

public class Theme {
    static TextViewTheme textViewTheme;
    static ViewGroupTheme viewGroupTheme;
    static IconTheme iconTheme;
    static SwitchTheme switchTheme;
    
    public static TextViewTheme getTextViewTheme() {
        return textViewTheme;
    }
    
    public static void setTextViewTheme(TextViewTheme textViewTheme) {
        Theme.textViewTheme = textViewTheme;
    }
    
    public static ViewGroupTheme getViewGroupTheme() {
        return viewGroupTheme;
    }
    
    public static void setViewGroupTheme(ViewGroupTheme viewGroupTheme) {
        Theme.viewGroupTheme = viewGroupTheme;
    }
    
    public static IconTheme getIconTheme() {
        return iconTheme;
    }
    
    public static void setIconTheme(IconTheme iconTheme) {
        Theme.iconTheme = iconTheme;
    }
    
    public static SwitchTheme getSwitchTheme() {
        return switchTheme;
    }
    
    public static void setSwitchTheme(SwitchTheme switchTheme) {
        Theme.switchTheme = switchTheme;
    }
}
