package app.simple.felicity.models.home;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class Home {
    
    @StringRes
    private int title;
    
    @DrawableRes
    private int icon;
    
    public Home(int title, int icon) {
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
