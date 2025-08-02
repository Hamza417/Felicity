package app.simple.felicity.models;

import java.util.List;

import androidx.annotation.StringRes;

public class ArtFlowData <T> {
    
    @StringRes
    private int title;
    
    private List <T> items;
    private int position = -1;
    
    public ArtFlowData(int title, List <T> items) {
        this.title = title;
        this.items = items;
    }
    
    public int getTitle() {
        return title;
    }
    
    public void setTitle(int title) {
        this.title = title;
    }
    
    public List <T> getItems() {
        return items;
    }
    
    public void setItems(List <T> items) {
        this.items = items;
    }
    
    public int getPosition() {
        return position;
    }
    
    public void setPosition(int position) {
        this.position = position;
    }
}
