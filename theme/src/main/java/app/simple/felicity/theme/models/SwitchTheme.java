package app.simple.felicity.theme.models;

public class SwitchTheme {
    
    private int switchOffColor;
    
    public SwitchTheme(int switchOffColor) {
        this.switchOffColor = switchOffColor;
    }
    
    public int getSwitchOffColor() {
        return switchOffColor;
    }
    
    public void setSwitchOffColor(int switchOffColor) {
        this.switchOffColor = switchOffColor;
    }
}
