package app.simple.felicity.repository.metadata;

import android.media.MediaMetadataRetriever;
import android.os.Build;

import java.io.File;

import app.simple.felicity.core.utils.FileUtils;
import app.simple.felicity.repository.models.Audio;

public class MediaMetadataLoader {
    
    private final File file;
    private final MediaMetadataRetriever retriever;
    
    public MediaMetadataLoader(File file) {
        this.file = file;
        this.retriever = new MediaMetadataRetriever();
        this.retriever.setDataSource(file.getAbsolutePath());
    }
    
    public MediaMetadataLoader(String path) {
        this(new File(path));
    }
    
    /**
     * @noinspection DataFlowIssue
     */
    public void setAudioMetadata(Audio audio) {
        audio.setName(file.getName());
        audio.setPath(file.getAbsolutePath());
        audio.setAlbum(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
        audio.setArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
        audio.setGenre(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
        audio.setYear(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR));
        audio.setDuration(Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
        audio.setTrackNumber(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
        audio.setNumTracks(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS));
        audio.setComposer(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));
        audio.setMimeType(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
        audio.setBitrate(Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)));
        
        // Additional fields
        audio.setAlbumArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
        audio.setWriter(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER));
        audio.setCompilation(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION));
        audio.setDate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
        audio.setDiscNumber(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.setSamplingRate(Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audio.setBitPerSample(Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)));
            } catch (NumberFormatException e) {
                audio.setBitPerSample(0);
            }
        } else {
            audio.setBitPerSample(0);
        }
        
        audio.setId(generateId(audio));
    }
    
    private long generateId(Audio audio) {
        return FileUtils.INSTANCE.generateXXHash64(new File(audio.getPath()), Integer.MAX_VALUE);
    }
}
