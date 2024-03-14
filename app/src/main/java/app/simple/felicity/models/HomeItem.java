package app.simple.felicity.models;

import java.util.ArrayList;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class HomeItem {
    
    private ArrayList <Audio> audios;
    
    @StringRes
    private int title;
    
    @DrawableRes
    private int icon;
    
    public HomeItem(int title, int icon, ArrayList <Audio> audios) {
        this.title = title;
        this.icon = icon;
        this.audios = audios;
    }
    
    public ArrayList <Audio> getAudios() {
        return audios;
    }
    
    public void setAudios(ArrayList <Audio> audios) {
        this.audios = audios;
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
