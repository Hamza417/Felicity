package app.simple.felicity.models.normal;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "audio")
public class Audio implements Parcelable {
    
    @ColumnInfo (name = "name")
    private String name;
    @ColumnInfo (name = "id")
    @PrimaryKey
    private long id;
    @ColumnInfo (name = "title")
    @Nullable
    private String title;
    @ColumnInfo (name = "artist")
    @Nullable
    private String artist;
    @ColumnInfo (name = "album")
    @Nullable
    private String album;
    @ColumnInfo (name = "path")
    private String path;
    @ColumnInfo (name = "track")
    private int track;
    @ColumnInfo (name = "author")
    @Nullable
    private String author;
    @ColumnInfo (name = "size")
    private int size;
    @ColumnInfo (name = "album_artist")
    @Nullable
    private String albumArtist;
    @ColumnInfo (name = "year")
    @Nullable
    private String year;
    @ColumnInfo (name = "bitrate")
    @Nullable
    private String bitrate;
    @ColumnInfo (name = "composer")
    @Nullable
    private String composer;
    @ColumnInfo (name = "duration")
    private long duration;
    @ColumnInfo (name = "date")
    @Nullable
    private String date;
    @ColumnInfo (name = "disc_number")
    @Nullable
    private String discNumber;
    @ColumnInfo (name = "genre")
    @Nullable
    private String genre;
    @ColumnInfo (name = "track_number")
    @Nullable
    private String trackNumber;
    @ColumnInfo (name = "date_added")
    private long dateAdded;
    @ColumnInfo (name = "date_modified")
    private long dateModified;
    @ColumnInfo (name = "date_taken")
    private long dateTaken;
    @ColumnInfo (name = "album_id")
    private long albumId;
    
    public Audio() {
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
        
        //        this.artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), String.valueOf(albumId)).toString();
        //        this.fileUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)).toString();
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
        if (columnIndex != -1) {
            this.path = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
        if (columnIndex != -1) {
            this.track = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
        if (columnIndex != -1) {
            this.year = String.valueOf(cursor.getInt(columnIndex));
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
        if (columnIndex != -1) {
            this.size = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE);
        if (columnIndex != -1) {
            this.bitrate = String.valueOf(cursor.getInt(columnIndex));
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
    }
    
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
    
    protected Audio(Parcel in) {
        name = in.readString();
        id = in.readLong();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        path = in.readString();
        track = in.readInt();
        author = in.readString();
        size = in.readInt();
        albumArtist = in.readString();
        year = in.readString();
        bitrate = in.readString();
        composer = in.readString();
        duration = in.readLong();
        date = in.readString();
        discNumber = in.readString();
        genre = in.readString();
        trackNumber = in.readString();
        dateAdded = in.readLong();
        dateModified = in.readLong();
        dateTaken = in.readLong();
        albumId = in.readLong();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(path);
        dest.writeInt(track);
        dest.writeString(author);
        dest.writeInt(size);
        dest.writeString(albumArtist);
        dest.writeString(year);
        dest.writeString(bitrate);
        dest.writeString(composer);
        dest.writeLong(duration);
        dest.writeString(date);
        dest.writeString(discNumber);
        dest.writeString(genre);
        dest.writeString(trackNumber);
        dest.writeLong(dateAdded);
        dest.writeLong(dateModified);
        dest.writeLong(dateTaken);
        dest.writeLong(albumId);
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
    
    @Nullable
    public String getTitle() {
        return title;
    }
    
    public void setTitle(@Nullable String title) {
        this.title = title;
    }
    
    @Nullable
    public String getArtist() {
        return artist;
    }
    
    public void setArtist(@Nullable String artist) {
        this.artist = artist;
    }
    
    @Nullable
    public String getAlbum() {
        return album;
    }
    
    public void setAlbum(@Nullable String album) {
        this.album = album;
    }
    
    public long getAlbumId() {
        return albumId;
    }
    
    public void setAlbumId(long albumId) {
        this.albumId = albumId;
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
    
    public int getTrack() {
        return track;
    }
    
    public void setTrack(int track) {
        this.track = track;
    }
    
    @Nullable
    public String getYear() {
        return year;
    }
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public void setYear(@Nullable String year) {
        this.year = year;
    }
    
    @Nullable
    public String getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(@Nullable String bitrate) {
        this.bitrate = bitrate;
    }
    
    @Nullable
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(@Nullable String author) {
        this.author = author;
    }
    
    @Nullable
    public String getAlbumArtist() {
        return albumArtist;
    }
    
    public void setAlbumArtist(@Nullable String albumArtist) {
        this.albumArtist = albumArtist;
    }
    
    @Nullable
    public String getComposer() {
        return composer;
    }
    
    public void setComposer(@Nullable String composer) {
        this.composer = composer;
    }
    
    @Nullable
    public String getDate() {
        return date;
    }
    
    public void setDate(@Nullable String date) {
        this.date = date;
    }
    
    @Nullable
    public String getDiscNumber() {
        return discNumber;
    }
    
    public void setDiscNumber(@Nullable String discNumber) {
        this.discNumber = discNumber;
    }
    
    @Nullable
    public String getGenre() {
        return genre;
    }
    
    public void setGenre(@Nullable String genre) {
        this.genre = genre;
    }
    
    @Nullable
    public String getTrackNumber() {
        return trackNumber;
    }
    
    public void setTrackNumber(@Nullable String trackNumber) {
        this.trackNumber = trackNumber;
    }
}
