package app.simple.felicity.loaders;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import androidx.annotation.RequiresApi;
import app.simple.felicity.models.normal.Audio;

public class MediaMetadataLoader {
    
    private Uri contentUri;
    private File file;
    private final MediaMetadataRetriever retriever;
    
    public MediaMetadataLoader(Uri contentUri, Context context) {
        this.contentUri = contentUri;
        this.retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, contentUri);
    }
    
    public MediaMetadataLoader(File file) {
        this.file = file;
        this.retriever = new MediaMetadataRetriever();
        retriever.setDataSource(file.getAbsolutePath());
    }
    
    public void close() {
        try {
            retriever.release();
        } catch (IOException e) {
            Log.e("MediaMetadataLoader", "Error releasing MediaMetadataRetriever", e);
        }
        
        try {
            retriever.close();
        } catch (IOException e) {
            Log.e("MediaMetadataLoader", "Error closing MediaMetadataRetriever", e);
        }
    }
    
    public void setAudioMetadata(Audio audio) {
        audio.setName(file.getName());
        audio.setPath(file.getAbsolutePath());
        audio.setAlbum(getAlbum());
        audio.setAlbumArtist(getAlbumArtist());
        audio.setArtist(getArtist());
        // audio.setAuthor(getAuthor());
        audio.setBitrate(getBitrate());
        audio.setCompilation(getCompilation());
        audio.setComposer(getComposer());
        audio.setDate(getDate());
        audio.setDiscNumber(getDiscNumber());
        audio.setDuration(getDuration());
        audio.setGenre(getGenre());
        audio.setMimeType(getMIMEType());
        audio.setNumTracks(getNumTracks());
        audio.setTitle(getTitle());
        audio.setTrackNumber(getTrackNumber());
        audio.setWriter(getWriter());
        audio.setYear(getYear());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.setSamplingRate(getSamplingRate());
            audio.setBitPerSample(getBitPerSample());
        }
    }
    
    private String getTrackNumber() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
    }
    
    private String getAlbum() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
    }
    
    private String getArtist() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
    }
    
    private String getAuthor() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);
    }
    
    private String getComposer() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
    }
    
    private String getDate() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
    }
    
    private String getGenre() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
    }
    
    private String getTitle() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
    }
    
    private String getYear() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
    }
    
    private long getDuration() {
        return Long.parseLong(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
    }
    
    private String getNumTracks() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
    }
    
    private String getWriter() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER);
    }
    
    private String getMIMEType() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
    }
    
    private String getAlbumArtist() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
    }
    
    private String getDiscNumber() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);
    }
    
    private String getCompilation() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION);
    }
    
    private String getHasAudio() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
    }
    
    private String getHasVideo() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
    }
    
    private String getBitrate() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
    }
    
    @RequiresApi (api = Build.VERSION_CODES.S)
    private String getSamplingRate() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
    }
    
    @RequiresApi (api = Build.VERSION_CODES.S)
    private String getBitPerSample() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE);
    }
}
