package app.simple.felicity.callbacks;

import android.view.View;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import app.simple.felicity.repository.models.Album;
import app.simple.felicity.repository.models.Artist;
import app.simple.felicity.repository.models.Audio;
import app.simple.felicity.repository.models.Genre;

public interface GeneralAdapterCallbacks {
    
    default void onPanelItemClicked(@StringRes int title, @NonNull View view) {
    
    }
    
    default void onSongClicked(List <Audio> songs, int position, View view) {
    
    }
    
    default void onSongLongClicked(List <Audio> songs, int position, View view) {
    
    }
    
    default void onPlayClicked(List <Audio> audios, int position) {
    
    }
    
    default void onShuffleClicked(List <Audio> audios, int position) {
    
    }
    
    default void onArtistClicked(@NonNull List <Artist> artist, int position, @NonNull View view) {
    
    }
    
    default void onArtistLongClicked(@NonNull List <Artist> artist, int position, @NonNull View view) {
    
    }
    
    default void onAlbumClicked(List <Album> albums, int position, View view) {
    
    }
    
    default void onAlbumLongClicked(List <Album> albums, int position, View view) {
    
    }
    
    default void onMenuClicked(@NonNull View view) {
    
    }
    
    default void onFilterClicked(@NonNull View view) {
    
    }
    
    default void onSortClicked(@NonNull View view) {
    
    }
    
    default void onSearchClicked(@NonNull View view) {
    
    }
    
    default void onGenreClicked(@NonNull Genre genre, @NonNull View view) {
    
    }
}
