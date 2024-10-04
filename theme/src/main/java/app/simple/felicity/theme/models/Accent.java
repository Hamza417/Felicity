package app.simple.felicity.theme.models;

import androidx.annotation.NonNull;

public class Accent {
    private int primaryAccentColor;
    private int secondaryAccentColor;

    public Accent(int primaryAccentColor, int secondaryAccentColor) {
        this.primaryAccentColor = primaryAccentColor;
        this.secondaryAccentColor = secondaryAccentColor;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Accent accent = (Accent) o;

        if (primaryAccentColor != accent.primaryAccentColor) return false;
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

        public Builder setPrimaryAccentColor(int primaryAccentColor) {
            this.primaryAccentColor = primaryAccentColor;
            return this;
        }

        public Builder setSecondaryAccentColor(int secondaryAccentColor) {
            this.secondaryAccentColor = secondaryAccentColor;
            return this;
        }

        public Accent build() {
            return new Accent(primaryAccentColor, secondaryAccentColor);
        }
    }
}
