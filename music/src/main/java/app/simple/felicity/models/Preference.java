package app.simple.felicity.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class Preference implements Parcelable {
    
    @StringRes
    private int title;
    @StringRes
    private int summary;
    @DrawableRes
    private int icon;
    private boolean isSwitch;
    private boolean isChecked;
    private boolean isOptions;
    private boolean isPopup;
    
    public Preference() {
    }
    
    protected Preference(Parcel in) {
        title = in.readInt();
        summary = in.readInt();
        icon = in.readInt();
        isSwitch = in.readByte() != 0;
        isChecked = in.readByte() != 0;
        isOptions = in.readByte() != 0;
        isPopup = in.readByte() != 0;
    }
    
    public static final Creator <Preference> CREATOR = new Creator <Preference>() {
        @Override
        public Preference createFromParcel(Parcel in) {
            return new Preference(in);
        }
        
        @Override
        public Preference[] newArray(int size) {
            return new Preference[size];
        }
    };
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(title);
        dest.writeInt(summary);
        dest.writeInt(icon);
        dest.writeByte((byte) (isSwitch ? 1 : 0));
        dest.writeByte((byte) (isChecked ? 1 : 0));
        dest.writeByte((byte) (isOptions ? 1 : 0));
        dest.writeByte((byte) (isPopup ? 1 : 0));
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public int getTitle() {
        return title;
    }
    
    public void setTitle(int title) {
        this.title = title;
    }
    
    public int getSummary() {
        return summary;
    }
    
    public void setSummary(int summary) {
        this.summary = summary;
    }
    
    public int getIcon() {
        return icon;
    }
    
    public void setIcon(int icon) {
        this.icon = icon;
    }
    
    public boolean isSwitch() {
        return isSwitch;
    }
    
    public void setSwitch(boolean aSwitch) {
        isSwitch = aSwitch;
    }
    
    public boolean isChecked() {
        return isChecked;
    }
    
    public void setChecked(boolean checked) {
        isChecked = checked;
    }
    
    public boolean isOptions() {
        return isOptions;
    }
    
    public void setOptions(boolean options) {
        isOptions = options;
    }
    
    public boolean isPopup() {
        return isPopup;
    }
    
    public void setPopup(boolean popup) {
        isPopup = popup;
    }
    
}
