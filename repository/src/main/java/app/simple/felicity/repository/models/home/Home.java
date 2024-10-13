package app.simple.felicity.repository.models.home;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class Home {
    
    @StringRes
    private int title;
    
    @DrawableRes
    private int icon;
    
    private int size;
    
    public Home(int title, int icon, int size) {
        this.title = title;
        this.icon = icon;
        this.size = size;
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
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
}
