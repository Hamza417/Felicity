package app.simple.felicity.theme.models;

public class IconTheme {
    
    private int regularIconColor;
    private int secondaryIconColor;
    private int disabledIconColor;
    
    public IconTheme(int regularIconColor, int secondaryIconColor, int disabledIconColor) {
        this.regularIconColor = regularIconColor;
        this.secondaryIconColor = secondaryIconColor;
        this.disabledIconColor = disabledIconColor;
    }
    
    public int getRegularIconColor() {
        return regularIconColor;
    }
    
    public void setRegularIconColor(int regularIconColor) {
        this.regularIconColor = regularIconColor;
    }
    
    public int getSecondaryIconColor() {
        return secondaryIconColor;
    }
    
    public void setSecondaryIconColor(int secondaryIconColor) {
        this.secondaryIconColor = secondaryIconColor;
    }
    
    public int getDisabledIconColor() {
        return disabledIconColor;
    }
    
    public void setDisabledIconColor(int disabledIconColor) {
        this.disabledIconColor = disabledIconColor;
    }
}
