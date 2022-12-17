package app.simple.felicity.models;

import android.os.Parcel;
import android.os.Parcelable;

public class Theme implements Parcelable {
    
    public static final Creator <Theme> CREATOR = new Creator <Theme>() {
        @Override
        public Theme createFromParcel(Parcel in) {
            return new Theme(in);
        }
        
        @Override
        public Theme[] newArray(int size) {
            return new Theme[size];
        }
    };
    private int headerTextColor;
    private int primaryTextColor;
    private int secondaryTextColor;
    private int tertiaryTextColor;
    private int quaternaryTextColor;
    private int backgroundColor;
    private int highlightBackgroundColor;
    private int dividerColor;
    private int switchColor;
    private int regularIconColor;
    private int selectedIconColor;
    private int disabledIconColor;
    private int secondaryIconColor;
    
    public Theme(int headerTextColor,
            int primaryTextColor,
            int secondaryTextColor,
            int tertiaryTextColor,
            int quaternaryTextColor,
            int backgroundColor,
            int highlightBackgroundColor,
            int dividerColor,
            int switchColor,
            int regularIconColor,
            int selectedIconColor,
            int disabledIconColor,
            int secondaryIconColor) {
        this.headerTextColor = headerTextColor;
        this.primaryTextColor = primaryTextColor;
        this.secondaryTextColor = secondaryTextColor;
        this.tertiaryTextColor = tertiaryTextColor;
        this.quaternaryTextColor = quaternaryTextColor;
        this.backgroundColor = backgroundColor;
        this.highlightBackgroundColor = highlightBackgroundColor;
        this.dividerColor = dividerColor;
        this.switchColor = switchColor;
        this.regularIconColor = regularIconColor;
        this.selectedIconColor = selectedIconColor;
        this.disabledIconColor = disabledIconColor;
        this.secondaryIconColor = secondaryIconColor;
    }
    
    public Theme() {
        // Empty constructor
    }
    
    protected Theme(Parcel in) {
        headerTextColor = in.readInt();
        primaryTextColor = in.readInt();
        secondaryTextColor = in.readInt();
        tertiaryTextColor = in.readInt();
        quaternaryTextColor = in.readInt();
        backgroundColor = in.readInt();
        highlightBackgroundColor = in.readInt();
        dividerColor = in.readInt();
        switchColor = in.readInt();
        regularIconColor = in.readInt();
        selectedIconColor = in.readInt();
        disabledIconColor = in.readInt();
        secondaryIconColor = in.readInt();
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
    
    public int getBackgroundColor() {
        return backgroundColor;
    }
    
    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
    
    public int getHighlightBackgroundColor() {
        return highlightBackgroundColor;
    }
    
    public void setHighlightBackgroundColor(int highlightBackgroundColor) {
        this.highlightBackgroundColor = highlightBackgroundColor;
    }
    
    public int getDividerColor() {
        return dividerColor;
    }
    
    public void setDividerColor(int dividerColor) {
        this.dividerColor = dividerColor;
    }
    
    public int getSwitchColor() {
        return switchColor;
    }
    
    public void setSwitchColor(int switchColor) {
        this.switchColor = switchColor;
    }
    
    public int getRegularIconColor() {
        return regularIconColor;
    }
    
    public void setRegularIconColor(int regularIconColor) {
        this.regularIconColor = regularIconColor;
    }
    
    public int getSelectedIconColor() {
        return selectedIconColor;
    }
    
    public void setSelectedIconColor(int selectedIconColor) {
        this.selectedIconColor = selectedIconColor;
    }
    
    public int getDisabledIconColor() {
        return disabledIconColor;
    }
    
    public void setDisabledIconColor(int disabledIconColor) {
        this.disabledIconColor = disabledIconColor;
    }
    
    public int getSecondaryIconColor() {
        return secondaryIconColor;
    }
    
    public void setSecondaryIconColor(int secondaryIconColor) {
        this.secondaryIconColor = secondaryIconColor;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(headerTextColor);
        parcel.writeInt(primaryTextColor);
        parcel.writeInt(secondaryTextColor);
        parcel.writeInt(tertiaryTextColor);
        parcel.writeInt(quaternaryTextColor);
        parcel.writeInt(backgroundColor);
        parcel.writeInt(highlightBackgroundColor);
        parcel.writeInt(dividerColor);
        parcel.writeInt(switchColor);
        parcel.writeInt(regularIconColor);
        parcel.writeInt(selectedIconColor);
        parcel.writeInt(disabledIconColor);
        parcel.writeInt(secondaryIconColor);
    }
}
