package app.simple.felicity.theme.models;

import androidx.annotation.NonNull;

public class Accent {
    private int primaryAccentColor;
    private int secondaryAccentColor;
    private String identifier;
    
    public Accent(int primaryAccentColor, int secondaryAccentColor, String identifier) {
        this.primaryAccentColor = primaryAccentColor;
        this.secondaryAccentColor = secondaryAccentColor;
        this.identifier = identifier;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public int getPrimaryAccentColor() {
        return primaryAccentColor;
    }
    
    public void setPrimaryAccentColor(int primaryAccentColor) {
        this.primaryAccentColor = primaryAccentColor;
    }
    
    public int getSecondaryAccentColor() {
        return secondaryAccentColor;
    }
    
    public void setSecondaryAccentColor(int secondaryAccentColor) {
        this.secondaryAccentColor = secondaryAccentColor;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public String getHexes() {
        StringBuilder primaryHex = new StringBuilder(Integer.toHexString(primaryAccentColor));
        StringBuilder secondaryHex = new StringBuilder(Integer.toHexString(secondaryAccentColor));
        
        // Pad with leading zeros if necessary
        while (primaryHex.length() < 6) {
            primaryHex.insert(0, "0");
        }
        while (secondaryHex.length() < 6) {
            secondaryHex.insert(0, "0");
        }
        
        return "#" + primaryHex.toString().toUpperCase() + ", #" + secondaryHex.toString().toUpperCase();
    }
    
    @NonNull
    @Override
    public String toString() {
        return "Accent{" +
                "primaryAccentColor=" + primaryAccentColor +
                ", secondaryAccentColor=" + secondaryAccentColor +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        Accent accent = (Accent) o;
        
        if (primaryAccentColor != accent.primaryAccentColor) {
            return false;
        }
        return secondaryAccentColor == accent.secondaryAccentColor;
    }
    
    @Override
    public int hashCode() {
        int result = primaryAccentColor;
        result = 31 * result + secondaryAccentColor;
        return result;
    }
    
    public static class Builder {
        private int primaryAccentColor;
        private int secondaryAccentColor;
        private String name;
        
        public Builder setPrimaryAccentColor(int primaryAccentColor) {
            this.primaryAccentColor = primaryAccentColor;
            return this;
        }
        
        public Builder setSecondaryAccentColor(int secondaryAccentColor) {
            this.secondaryAccentColor = secondaryAccentColor;
            return this;
        }
        
        public Builder setName(String name) {
            this.name = name;
            return this;
        }
        
        public Accent build() {
            return new Accent(primaryAccentColor, secondaryAccentColor, name);
        }
    }
}
