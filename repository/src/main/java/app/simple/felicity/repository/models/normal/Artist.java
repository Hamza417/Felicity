package app.simple.felicity.repository.models.normal;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class Artist implements Parcelable, Serializable {
    
    private int id;
    private String artistName;
    public static final Creator <Artist> CREATOR = new Creator <Artist>() {
        @Override
        public Artist createFromParcel(Parcel in) {
            return new Artist(in);
        }
        
        @Override
        public Artist[] newArray(int size) {
            return new Artist[size];
        }
    };
    private int numberOfAlbums;
    private int numberOfSongs;
    
    public Artist() {
    }
    
    public Artist(Cursor cursor) {
        int columnIndex;
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Artists._ID);
        if (columnIndex != -1) {
            id = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST);
        if (columnIndex != -1) {
            artistName = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS);
        if (columnIndex != -1) {
            numberOfAlbums = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Artists.NUMBER_OF_TRACKS);
        if (columnIndex != -1) {
            numberOfSongs = cursor.getInt(columnIndex);
        }
    }
    
    protected Artist(Parcel in) {
        id = in.readInt();
        artistName = in.readString();
        artUri = in.readString();
        numberOfAlbums = in.readInt();
        numberOfSongs = in.readInt();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(artistName);
        dest.writeString(artUri);
        dest.writeInt(numberOfAlbums);
        dest.writeInt(numberOfSongs);
    }
    private String artUri;
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getArtistName() {
        return artistName;
    }
    
    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }
    
    public int getNumberOfAlbums() {
        return numberOfAlbums;
    }
    
    public void setNumberOfAlbums(int numberOfAlbums) {
        this.numberOfAlbums = numberOfAlbums;
    }
    
    public int getNumberOfSongs() {
        return numberOfSongs;
    }
    
    public void setNumberOfSongs(int numberOfSongs) {
        this.numberOfSongs = numberOfSongs;
    }
    
    public String getArtUri() {
        return artUri;
    }
    
    public void setArtUri(String artUri) {
        this.artUri = artUri;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        Artist artist = (Artist) o;
        
        if (getId() != artist.getId()) {
            return false;
        }
        if (getNumberOfAlbums() != artist.getNumberOfAlbums()) {
            return false;
        }
        if (getNumberOfSongs() != artist.getNumberOfSongs()) {
            return false;
        }
        return getArtistName() != null ? getArtistName().equals(artist.getArtistName()) : artist.getArtistName() == null;
    }
    
    @Override
    public int hashCode() {
        int result = getId();
        result = 31 * result + (getArtistName() != null ? getArtistName().hashCode() : 0);
        result = 31 * result + getNumberOfAlbums();
        result = 31 * result + getNumberOfSongs();
        return result;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "Artist{" +
                "id=" + id +
                ", artistName='" + artistName + '\'' +
                ", numberOfAlbums=" + numberOfAlbums +
                ", numberOfSongs=" + numberOfSongs +
                '}';
    }
}
