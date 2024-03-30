package app.simple.felicity.loaders;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class MediaMetadataLoader {
    
    private final Uri contentUri;
    private final MediaMetadataRetriever retriever;
    
    public MediaMetadataLoader(Uri contentUri, Context context) {
        this.contentUri = contentUri;
        this.retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, contentUri);
    }
    
    private String getCDTrackNumber() {
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
    
    private String getDuration() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
    }
    
    private String getNumTracks() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
    }
    
    private String getWriter() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER);
    }
    
    private String getMIMIType() {
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
    
    private String getVideoWidth() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
    }
    
    private String getVideoHeight() {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
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
