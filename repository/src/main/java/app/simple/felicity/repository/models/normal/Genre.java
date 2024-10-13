package app.simple.felicity.repository.models.normal;

import android.database.Cursor;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import java.util.Objects;

public class Genre {
    private int id;
    private String genreName;
    
    public Genre() {
    }
    
    public Genre(Cursor cursor) {
        int columnIndex;
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Genres._ID);
        if (columnIndex != -1) {
            this.id = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME);
        if (columnIndex != -1) {
            this.genreName = cursor.getString(columnIndex);
        }
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getGenreName() {
        return genreName;
    }
    
    public void setGenreName(String genreName) {
        this.genreName = genreName;
    }
    
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            Genre genre = (Genre) o;
            if (this.id != genre.id) {
                return false;
            } else {
                return Objects.equals(this.genreName, genre.genreName);
            }
        } else {
            return false;
        }
    }
    
    public int hashCode() {
        int result = this.id;
        result = 31 * result + (this.genreName != null ? this.genreName.hashCode() : 0);
        return result;
    }
    
    @NonNull
    public String toString() {
        return "Genres{id=" + this.id + ", genreName=" + this.genreName + '}';
    }
}
