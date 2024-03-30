package app.simple.felicity.models.normal;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import java.io.Serializable;

import androidx.annotation.NonNull;

public class Album implements Parcelable, Serializable {
    
    public static final Creator <Album> CREATOR = new Creator <Album>() {
        @Override
        public Album createFromParcel(Parcel in) {
            return new Album(in);
        }
        
        @Override
        public Album[] newArray(int size) {
            return new Album[size];
        }
    };
    private int id;
    private long albumId;
    private String albumName;
    private int artistId;
    private String artistName;
    private int numberOfSongs;
    private int numberOfSongsForArtist;
    private long firstYear;
    private long lastYear;
    
    public Album() {
    }
    
    @SuppressLint ("InlinedApi")
    public Album(Cursor cursor) {
        int columnIndex;
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums._ID);
        if (columnIndex != -1) {
            this.id = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ID);
        if (columnIndex != -1) {
            this.albumId = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM);
        if (columnIndex != -1) {
            this.albumName = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST_ID);
        if (columnIndex != -1) {
            this.artistId = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST);
        if (columnIndex != -1) {
            this.artistName = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.NUMBER_OF_SONGS);
        if (columnIndex != -1) {
            this.numberOfSongs = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.NUMBER_OF_SONGS_FOR_ARTIST);
        if (columnIndex != -1) {
            this.numberOfSongsForArtist = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.FIRST_YEAR);
        if (columnIndex != -1) {
            this.firstYear = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.LAST_YEAR);
        if (columnIndex != -1) {
            this.lastYear = cursor.getLong(columnIndex);
        }
    }
    
    protected Album(Parcel in) {
        id = in.readInt();
        albumId = in.readInt();
        albumName = in.readString();
        artistId = in.readInt();
        artistName = in.readString();
        numberOfSongs = in.readInt();
        numberOfSongsForArtist = in.readInt();
        firstYear = in.readLong();
        lastYear = in.readLong();
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public long getAlbumId() {
        return albumId;
    }
    
    public void setAlbumId(int albumId) {
        this.albumId = albumId;
    }
    
    public String getAlbumName() {
        return albumName;
    }
    
    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }
    
    public int getArtistId() {
        return artistId;
    }
    
    public void setArtistId(int artistId) {
        this.artistId = artistId;
    }
    
    public String getArtistName() {
        return artistName;
    }
    
    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }
    
    public int getNumberOfSongs() {
        return numberOfSongs;
    }
    
    public void setNumberOfSongs(int numberOfSongs) {
        this.numberOfSongs = numberOfSongs;
    }
    
    public int getNumberOfSongsForArtist() {
        return numberOfSongsForArtist;
    }
    
    public void setNumberOfSongsForArtist(int numberOfSongsForArtist) {
        this.numberOfSongsForArtist = numberOfSongsForArtist;
    }
    
    public long getFirstYear() {
        return firstYear;
    }
    
    public void setFirstYear(long firstYear) {
        this.firstYear = firstYear;
    }
    
    public long getLastYear() {
        return lastYear;
    }
    
    public void setLastYear(long lastYear) {
        this.lastYear = lastYear;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeLong(albumId);
        dest.writeString(albumName);
        dest.writeInt(artistId);
        dest.writeString(artistName);
        dest.writeInt(numberOfSongs);
        dest.writeInt(numberOfSongsForArtist);
        dest.writeLong(firstYear);
        dest.writeLong(lastYear);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        Album album = (Album) o;
        
        if (getId() != album.getId()) {
            return false;
        }
        if (getAlbumId() != album.getAlbumId()) {
            return false;
        }
        if (getArtistId() != album.getArtistId()) {
            return false;
        }
        if (getNumberOfSongs() != album.getNumberOfSongs()) {
            return false;
        }
        if (getNumberOfSongsForArtist() != album.getNumberOfSongsForArtist()) {
            return false;
        }
        if (getFirstYear() != album.getFirstYear()) {
            return false;
        }
        if (getLastYear() != album.getLastYear()) {
            return false;
        }
        if (!getAlbumName().equals(album.getAlbumName())) {
            return false;
        }
        return getArtistName().equals(album.getArtistName());
    }
    
    @Override
    public int hashCode() {
        int result = getId();
        result = 31 * result + (int) (getAlbumId() ^ (getAlbumId() >>> 32));
        result = 31 * result + getAlbumName().hashCode();
        result = 31 * result + getArtistId();
        result = 31 * result + getArtistName().hashCode();
        result = 31 * result + getNumberOfSongs();
        result = 31 * result + getNumberOfSongsForArtist();
        result = 31 * result + (int) (getFirstYear() ^ (getFirstYear() >>> 32));
        result = 31 * result + (int) (getLastYear() ^ (getLastYear() >>> 32));
        return result;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "Album{" +
                "id=" + id +
                ", albumId=" + albumId +
                ", albumName='" + albumName + '\'' +
                ", artistId=" + artistId +
                ", artistName='" + artistName + '\'' +
                ", numberOfSongs=" + numberOfSongs +
                ", numberOfSongsForArtist=" + numberOfSongsForArtist +
                ", firstYear=" + firstYear +
                ", lastYear=" + lastYear +
                '}';
    }
}
