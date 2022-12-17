package app.simple.felicity.models;

import android.os.Parcel;
import android.os.Parcelable;

public class AccentColor implements Parcelable {
    
    public static final Creator <AccentColor> CREATOR = new Creator <AccentColor>() {
        @Override
        public AccentColor createFromParcel(Parcel in) {
            return new AccentColor(in);
        }
        
        @Override
        public AccentColor[] newArray(int size) {
            return new AccentColor[size];
        }
    };
    private int accentColor;
    private String accentName;
    private String accentHex;
    
    public AccentColor(int accentColor, String accentName, String accentHex) {
        this.accentColor = accentColor;
        this.accentName = accentName;
        this.accentHex = accentHex;
    }
    
    public AccentColor() {
        //
    }
    
    protected AccentColor(Parcel in) {
        accentColor = in.readInt();
        accentName = in.readString();
        accentHex = in.readString();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(accentColor);
        dest.writeString(accentName);
        dest.writeString(accentHex);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public int getAccentColor() {
        return accentColor;
    }
    
    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }
    
    public String getAccentName() {
        return accentName;
    }
    
    public void setAccentName(String accentName) {
        this.accentName = accentName;
    }
    
    public String getAccentHex() {
        return accentHex;
    }
    
    public void setAccentHex(String accentHex) {
        this.accentHex = accentHex;
    }
}
