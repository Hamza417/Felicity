package app.simple.felicity.callbacks;

import android.view.View;

import java.util.List;

import androidx.annotation.NonNull;
import app.simple.felicity.repository.models.Album;
import app.simple.felicity.repository.models.Artist;
import app.simple.felicity.repository.models.Genre;
import app.simple.felicity.repository.models.Song;

public interface GeneralAdapterCallbacks {
    default void onSongClicked(List <Song> songs, int position, View view) {
    
    }
    
    default void onSongLongClicked(List <Song> songs, int position, View view) {
    
    }
    
    default void onPlayClicked(List <Song> songs, int position) {
    
    }
    
    default void onShuffleClicked(List <Song> songs, int position) {
    
    }
    
    default void onArtistClicked(Artist artist) {
    
    }
    
    default void onAlbumClicked(Album album) {
    
    }
    
    default void onMenuClicked(@NonNull View view) {
    
    }
    
    default void onSearchClicked(@NonNull View view) {
    
    }
    
    default void onGenreClicked(@NonNull Genre genre, @NonNull View view) {
    
    }
}
