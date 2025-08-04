package app.simple.felicity.theme.models;

public class ViewGroupTheme {
    
    private int backgroundColor;
    private int highlightColor;
    private int selectedBackgroundColor;
    private int dividerColor;
    
    private int spotColor;
    
    public ViewGroupTheme(int backgroundColor, int highlightColor, int selectedBackgroundColor, int dividerColor, int spotColor) {
        this.backgroundColor = backgroundColor;
        this.highlightColor = highlightColor;
        this.selectedBackgroundColor = selectedBackgroundColor;
        this.dividerColor = dividerColor;
        this.spotColor = spotColor;
    }
    
    public int getBackgroundColor() {
        return backgroundColor;
    }
    
    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
    
    public int getHighlightColor() {
        return highlightColor;
    }
    
    public void setHighlightColor(int highlightColor) {
        this.highlightColor = highlightColor;
    }
    
    public int getSelectedBackgroundColor() {
        return selectedBackgroundColor;
    }
    
    public void setSelectedBackgroundColor(int selectedBackgroundColor) {
        this.selectedBackgroundColor = selectedBackgroundColor;
    }
    
    public int getDividerColor() {
        return dividerColor;
    }
    
    public void setDividerColor(int dividerColor) {
        this.dividerColor = dividerColor;
    }
    
    public int getSpotColor() {
        return spotColor;
    }
    
    public void setSpotColor(int spotColor) {
        this.spotColor = spotColor;
    }
}
