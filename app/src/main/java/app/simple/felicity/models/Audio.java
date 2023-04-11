package app.simple.felicity.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "audio")
public class Audio implements Parcelable {
    
    public static final Creator <Audio> CREATOR = new Creator <>() {
        @Override
        public Audio createFromParcel(Parcel in) {
            return new Audio(in);
        }
        
        @Override
        public Audio[] newArray(int size) {
            return new Audio[size];
        }
    };
    
    @ColumnInfo (name = "name")
    private String name;
    
    @ColumnInfo (name = "title")
    private String title;
    
    @ColumnInfo (name = "artist")
    private String artist;
    
    @ColumnInfo (name = "album")
    private String album;
    
    @ColumnInfo (name = "art_uri")
    private String artUri;
    
    @ColumnInfo (name = "file_uri")
    private String fileUri;
    
    @ColumnInfo (name = "path")
    private String path;
    
    @ColumnInfo (name = "mime_type")
    private String mimeType;
    
    @ColumnInfo (name = "track")
    private int track;
    
    @ColumnInfo (name = "year")
    private int year;
    
    @ColumnInfo (name = "size")
    private int size;
    
    @ColumnInfo (name = "bitrate")
    private int bitrate;
    
    @ColumnInfo (name = "duration")
    private long duration;
    
    @ColumnInfo (name = "id")
    @PrimaryKey
    private long id;
    
    @ColumnInfo (name = "date_added")
    private long dateAdded;
    
    @ColumnInfo (name = "date_modified")
    private long dateModified;
    
    @ColumnInfo (name = "date_taken")
    private long dateTaken;
    
    public Audio() {
    }
    
    public Audio(String name, String title, String artist, String album, String artUri, String fileUri, String path, String mimeType, int track, int year, int size, int bitrate, long duration, long id, long dateAdded, long dateModified, long dateTaken) {
        this.name = name;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.artUri = artUri;
        this.fileUri = fileUri;
        this.path = path;
        this.mimeType = mimeType;
        this.track = track;
        this.year = year;
        this.size = size;
        this.bitrate = bitrate;
        this.duration = duration;
        this.id = id;
        this.dateAdded = dateAdded;
        this.dateModified = dateModified;
        this.dateTaken = dateTaken;
    }
    
    protected Audio(Parcel in) {
        name = in.readString();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        artUri = in.readString();
        fileUri = in.readString();
        path = in.readString();
        mimeType = in.readString();
        track = in.readInt();
        year = in.readInt();
        size = in.readInt();
        bitrate = in.readInt();
        duration = in.readLong();
        id = in.readLong();
        dateAdded = in.readLong();
        dateModified = in.readLong();
        dateTaken = in.readLong();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(artUri);
        dest.writeString(fileUri);
        dest.writeString(path);
        dest.writeString(mimeType);
        dest.writeInt(track);
        dest.writeInt(year);
        dest.writeInt(size);
        dest.writeInt(bitrate);
        dest.writeLong(duration);
        dest.writeLong(id);
        dest.writeLong(dateAdded);
        dest.writeLong(dateModified);
        dest.writeLong(dateTaken);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getArtist() {
        return artist;
    }
    
    public void setArtist(String artist) {
        this.artist = artist;
    }
    
    public String getAlbum() {
        return album;
    }
    
    public void setAlbum(String album) {
        this.album = album;
    }
    
    public String getArtUri() {
        return artUri;
    }
    
    public void setArtUri(String artUri) {
        this.artUri = artUri;
    }
    
    public String getFileUri() {
        return fileUri;
    }
    
    public void setFileUri(String fileUri) {
        this.fileUri = fileUri;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getDateAdded() {
        return dateAdded;
    }
    
    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }
    
    public long getDateModified() {
        return dateModified;
    }
    
    public void setDateModified(long dateModified) {
        this.dateModified = dateModified;
    }
    
    public long getDateTaken() {
        return dateTaken;
    }
    
    public void setDateTaken(long dateTaken) {
        this.dateTaken = dateTaken;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    public int getTrack() {
        return track;
    }
    
    public void setTrack(int track) {
        this.track = track;
    }
    
    public int getYear() {
        return year;
    }
    
    public void setYear(int year) {
        this.year = year;
    }
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public int getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "AudioModel{" +
                "name='" + name + '\'' +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", artUri='" + artUri + '\'' +
                ", fileUri='" + fileUri + '\'' +
                ", path='" + path + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", track=" + track +
                ", year=" + year +
                ", size=" + size +
                ", duration=" + duration +
                ", id=" + id +
                ", dateAdded=" + dateAdded +
                ", dateModified=" + dateModified +
                ", dateTaken=" + dateTaken +
                '}';
    }
}
