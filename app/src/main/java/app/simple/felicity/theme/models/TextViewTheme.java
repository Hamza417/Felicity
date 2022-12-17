package app.simple.felicity.theme.models;

public class TextViewTheme {
    
    private int headerTextColor;
    private int primaryTextColor;
    private int secondaryTextColor;
    private int tertiaryTextColor;
    private int quaternaryTextColor;
    
    public TextViewTheme(int headerTextColor, int primaryTextColor, int secondaryTextColor, int tertiaryTextColor, int quaternaryTextColor) {
        this.headerTextColor = headerTextColor;
        this.primaryTextColor = primaryTextColor;
        this.secondaryTextColor = secondaryTextColor;
        this.tertiaryTextColor = tertiaryTextColor;
        this.quaternaryTextColor = quaternaryTextColor;
    }
    
    public int getHeaderTextColor() {
        return headerTextColor;
    }
    
    public void setHeaderTextColor(int headerTextColor) {
        this.headerTextColor = headerTextColor;
    }
    
    public int getPrimaryTextColor() {
        return primaryTextColor;
    }
    
    public void setPrimaryTextColor(int primaryTextColor) {
        this.primaryTextColor = primaryTextColor;
    }
    
    public int getSecondaryTextColor() {
        return secondaryTextColor;
    }
    
    public void setSecondaryTextColor(int secondaryTextColor) {
        this.secondaryTextColor = secondaryTextColor;
    }
    
    public int getTertiaryTextColor() {
        return tertiaryTextColor;
    }
    
    public void setTertiaryTextColor(int tertiaryTextColor) {
        this.tertiaryTextColor = tertiaryTextColor;
    }
    
    public int getQuaternaryTextColor() {
        return quaternaryTextColor;
    }
    
    public void setQuaternaryTextColor(int quaternaryTextColor) {
        this.quaternaryTextColor = quaternaryTextColor;
    }
}
