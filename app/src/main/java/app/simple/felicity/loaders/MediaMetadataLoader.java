package app.simple.felicity.loaders;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.Objects;

import app.simple.felicity.models.normal.Audio;
import wseemann.media.FFmpegMediaMetadataRetriever;

public class MediaMetadataLoader {
    
    private Uri contentUri;
    private File file;
    private final FFmpegMediaMetadataRetriever retriever;
    
    public MediaMetadataLoader(Uri contentUri, Context context) {
        this.contentUri = contentUri;
        this.retriever = new FFmpegMediaMetadataRetriever();
        retriever.setDataSource(context, contentUri);
    }
    
    public MediaMetadataLoader(File file) {
        this.file = file;
        this.retriever = new FFmpegMediaMetadataRetriever();
        retriever.setDataSource(file.getAbsolutePath());
    }
    
    public void close() {
        retriever.release();
    }
    
    public void setAudioMetadata(Audio audio) {
        audio.setName(file.getName());
        audio.setPath(file.getAbsolutePath());
        audio.setAlbum(getAlbum());
        audio.setAlbumArtist(getAlbumArtist());
        audio.setArtist(getArtist());
        // audio.setBitrate(getBitrate());
        audio.setComposer(getComposer());
        audio.setDateTaken(getDate());
        // audio.setDiscNumber(getDiscNumber());
        audio.setDuration(getDuration());
        // audio.setGenre(getGenre());
        audio.setTitle(getTitle());
        audio.setTrackNumber(getTrackNumber());
        // audio.setYear(getYear());
    }
    
    private int getTrackNumber() {
        return Integer.parseInt(retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_TRACK));
    }
    
    private String getAlbum() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ALBUM);
    }
    
    private String getArtist() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ARTIST);
    }
    
    private String getComposer() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_COMPOSER);
    }
    
    private long getDate() {
        return Long.parseLong(retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DATE));
    }
    
    private String getGenre() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_GENRE);
    }
    
    private String getTitle() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_TITLE);
    }
    
    private String getYear() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DATE);
    }
    
    private long getDuration() {
        return Long.parseLong(Objects.requireNonNull(retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)));
    }
    
    private String getAlbumArtist() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ALBUM_ARTIST);
    }
    
    private String getDiscNumber() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DISC);
    }
    
    private String getBitrate() {
        return retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VARIANT_BITRATE);
    }
}
