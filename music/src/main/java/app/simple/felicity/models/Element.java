package app.simple.felicity.models;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class Element {
    
    @StringRes
    private int title;
    
    @DrawableRes
    private int icon;
    
    public Element(int title, int icon) {
        this.title = title;
        this.icon = icon;
    }
    
    public int getTitle() {
        return title;
    }
    
    public void setTitle(int title) {
        this.title = title;
    }
    
    public int getIcon() {
        return icon;
    }
    
    public void setIcon(int icon) {
        this.icon = icon;
    }
}
