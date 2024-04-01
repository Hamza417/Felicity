package app.simple.felicity.models.normal;

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
    @ColumnInfo (name = "album_artist")
    private String albumArtist;
    @ColumnInfo (name = "art_uri")
    private String artUri;
    
    @ColumnInfo (name = "file_uri")
    private String fileUri;
    
    @ColumnInfo (name = "path")
    private String path;
    
    @ColumnInfo (name = "mime_type")
    private String mimeType;
    
    @ColumnInfo (name = "composer")
    private String composer;
    
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
    
    @ColumnInfo (name = "disk_number")
    private int diskNumber;
    
    @ColumnInfo (name = "track_number")
    private int trackNumber;
    
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
    
    @SuppressLint ("Range")
    public Audio(Cursor cursor) {
        int columnIndex;
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
        if (columnIndex != -1) {
            this.id = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
        if (columnIndex != -1) {
            this.name = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
        if (columnIndex != -1) {
            this.title = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        if (columnIndex != -1) {
            this.artist = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        if (columnIndex != -1) {
            this.album = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
        if (columnIndex != -1) {
            this.albumId = cursor.getLong(columnIndex);
        }
        
        this.artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), String.valueOf(albumId)).toString();
        this.fileUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)).toString();
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
        if (columnIndex != -1) {
            this.path = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
        if (columnIndex != -1) {
            this.mimeType = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
        if (columnIndex != -1) {
            this.track = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
        if (columnIndex != -1) {
            this.year = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
        if (columnIndex != -1) {
            this.size = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE);
        if (columnIndex != -1) {
            this.bitrate = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
        if (columnIndex != -1) {
            this.duration = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);
        if (columnIndex != -1) {
            this.dateAdded = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED);
        if (columnIndex != -1) {
            this.dateModified = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_TAKEN);
        if (columnIndex != -1) {
            this.dateTaken = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER);
        if (columnIndex != -1) {
            this.composer = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER);
        if (columnIndex != -1) {
            this.diskNumber = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
        if (columnIndex != -1) {
            this.trackNumber = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST);
        if (columnIndex != -1) {
            this.albumArtist = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER);
        if (columnIndex != -1) {
            this.diskNumber = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
        if (columnIndex != -1) {
            this.trackNumber = cursor.getInt(columnIndex);
        }
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
    
    public String getArtist() {
        return artist;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getAlbum() {
        return album;
    }
    
    public void setArtist(String artist) {
        this.artist = artist;
    }
    
    public void setAlbum(String album) {
        this.album = album;
    }
    
    public String getAlbumArtist() {
        return albumArtist;
    }
    
    public long getAlbumId() {
        return albumId;
    }
    
    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }
    
    public void setAlbumArtist(String albumArtist) {
        this.albumArtist = albumArtist;
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
    
    public void setFileUri(String fileUri) {
        this.fileUri = fileUri;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public int getTrack() {
        return track;
    }
    
    public void setTrack(int track) {
        this.track = track;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    public int getYear() {
        return year;
    }
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public void setYear(int year) {
        this.year = year;
    }
    
    public int getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }
    
    public String getComposer() {
        return composer;
    }
    
    public void setComposer(String composer) {
        this.composer = composer;
    }
    
    public int getDiskNumber() {
        return diskNumber;
    }
    
    public void setDiskNumber(int diskNumber) {
        this.diskNumber = diskNumber;
    }
    
    public int getTrackNumber() {
        return trackNumber;
    }
    
    public void setTrackNumber(int trackNumber) {
        this.trackNumber = trackNumber;
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
}
