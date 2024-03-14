package app.simple.felicity.models;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "audio")
public class Audio implements Parcelable {
    
    @ColumnInfo (name = "name")
    private String name;
    
    @ColumnInfo (name = "title")
    private String title;
    
    @ColumnInfo (name = "artist")
    private String artist;
    
    @ColumnInfo (name = "album")
    private String album;
    
    public static final Creator <Audio> CREATOR = new Creator <Audio>() {
        @Override
        public Audio createFromParcel(Parcel in) {
            return new Audio(in);
        }
        
        @Override
        public Audio[] newArray(int size) {
            return new Audio[size];
        }
    };
    
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
    @ColumnInfo (name = "album_id")
    private long albumId;
    
    protected Audio(Parcel in) {
        name = in.readString();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        albumId = in.readLong();
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
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeLong(albumId);
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
    
    public long getAlbumId() {
        return albumId;
    }
    
    public void setAlbumId(long albumId) {
        this.albumId = albumId;
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        Audio audio = (Audio) o;
        
        if (getTrack() != audio.getTrack()) {
            return false;
        }
        if (getYear() != audio.getYear()) {
            return false;
        }
        if (getSize() != audio.getSize()) {
            return false;
        }
        if (getBitrate() != audio.getBitrate()) {
            return false;
        }
        if (getDuration() != audio.getDuration()) {
            return false;
        }
        if (getId() != audio.getId()) {
            return false;
        }
        if (getDateAdded() != audio.getDateAdded()) {
            return false;
        }
        if (getDateModified() != audio.getDateModified()) {
            return false;
        }
        if (getDateTaken() != audio.getDateTaken()) {
            return false;
        }
        if (getName() != null ? !getName().equals(audio.getName()) : audio.getName() != null) {
            return false;
        }
        if (getTitle() != null ? !getTitle().equals(audio.getTitle()) : audio.getTitle() != null) {
            return false;
        }
        if (getArtist() != null ? !getArtist().equals(audio.getArtist()) : audio.getArtist() != null) {
            return false;
        }
        if (getAlbum() != null ? !getAlbum().equals(audio.getAlbum()) : audio.getAlbum() != null) {
            return false;
        }
        if (getArtUri() != null ? !getArtUri().equals(audio.getArtUri()) : audio.getArtUri() != null) {
            return false;
        }
        if (getFileUri() != null ? !getFileUri().equals(audio.getFileUri()) : audio.getFileUri() != null) {
            return false;
        }
        if (getPath() != null ? !getPath().equals(audio.getPath()) : audio.getPath() != null) {
            return false;
        }
        return getMimeType() != null ? getMimeType().equals(audio.getMimeType()) : audio.getMimeType() == null;
    }
    
    @Override
    public int hashCode() {
        int result = getName() != null ? getName().hashCode() : 0;
        result = 31 * result + (getTitle() != null ? getTitle().hashCode() : 0);
        result = 31 * result + (getArtist() != null ? getArtist().hashCode() : 0);
        result = 31 * result + (getAlbum() != null ? getAlbum().hashCode() : 0);
        result = 31 * result + (int) (getAlbumId() ^ (getAlbumId() >>> 32));
        result = 31 * result + (getArtUri() != null ? getArtUri().hashCode() : 0);
        result = 31 * result + (getFileUri() != null ? getFileUri().hashCode() : 0);
        result = 31 * result + (getPath() != null ? getPath().hashCode() : 0);
        result = 31 * result + (getMimeType() != null ? getMimeType().hashCode() : 0);
        result = 31 * result + getTrack();
        result = 31 * result + getYear();
        result = 31 * result + getSize();
        result = 31 * result + getBitrate();
        result = 31 * result + (int) (getDuration() ^ (getDuration() >>> 32));
        result = 31 * result + (int) (getId() ^ (getId() >>> 32));
        result = 31 * result + (int) (getDateAdded() ^ (getDateAdded() >>> 32));
        result = 31 * result + (int) (getDateModified() ^ (getDateModified() >>> 32));
        result = 31 * result + (int) (getDateTaken() ^ (getDateTaken() >>> 32));
        return result;
    }
    
    @SuppressLint ("Range")
    public void setFromCursor(Cursor cursor) {
        this.name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
        this.title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
        this.artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
        this.album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
        this.albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
        this.artUri = Uri.withAppendedPath(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, String.valueOf(albumId)).toString();
        this.fileUri = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
        this.path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
        this.mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE));
        this.track = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.TRACK));
        this.year = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.YEAR));
        this.size = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE));
        this.bitrate = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE));
        this.duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
        this.id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
        this.dateAdded = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED));
        this.dateModified = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED));
        this.dateTaken = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DATE_TAKEN));
    }
}
