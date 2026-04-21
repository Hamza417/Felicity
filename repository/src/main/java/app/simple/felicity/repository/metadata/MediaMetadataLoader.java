package app.simple.felicity.repository.metadata;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

import java.io.File;

import app.simple.felicity.repository.models.Audio;

/**
 * Loads audio metadata using Android's built-in {@link MediaMetadataRetriever}.
 * <p>
 * Supports both traditional file paths and SAF content URIs so the app can
 * read tags from any folder the user has granted access to — not just files
 * it has direct filesystem access to.
 */
public class MediaMetadataLoader {
    
    private final File file;
    private final Uri contentUri;
    private final MediaMetadataRetriever retriever;
    /**
     * Cached size for SAF-loaded files (we can't call File.length() on a URI).
     */
    private long cachedSize = 0L;
    /**
     * Cached last-modified for SAF-loaded files.
     */
    private long cachedLastModified = 0L;
    
    private MediaMetadataLoader(File file) {
        this.file = file;
        this.contentUri = null;
        this.retriever = new MediaMetadataRetriever();
        this.retriever.setDataSource(file.getAbsolutePath());
    }
    
    /**
     * Constructs a loader for a content URI (SAF path). The size and last-modified
     * values must be supplied externally because we cannot get them from a Uri the
     * way we can from a File object.
     */
    private MediaMetadataLoader(Context context, Uri uri, long fileSize, long lastModified) {
        this.file = null;
        this.contentUri = uri;
        this.retriever = new MediaMetadataRetriever();
        this.retriever.setDataSource(context, uri);
        this.cachedSize = fileSize;
        this.cachedLastModified = lastModified;
    }
    
    /**
     * Load audio metadata from a file and return a populated Audio object.
     *
     * @param file The audio file to load metadata from.
     * @return Audio object with metadata populated from the file.
     */
    public static Audio loadFromFile(File file) {
        MediaMetadataLoader loader = new MediaMetadataLoader(file);
        return loader.createAudio();
    }
    
    /**
     * Load audio metadata from a file path and return a populated Audio object.
     *
     * @param path The path to the audio file.
     * @return Audio object with metadata populated from the file.
     */
    public static Audio loadFromFile(String path) {
        return loadFromFile(new File(path));
    }
    
    /**
     * Load audio metadata from a SAF content URI.
     * <p>
     * We need the size and last-modified values from the caller (via
     * {@link android.provider.DocumentsContract} or {@link androidx.documentfile.provider.DocumentFile})
     * because a bare {@link Uri} has no built-in way to report those numbers.
     *
     * @param context      Android context, required by MediaMetadataRetriever.
     * @param uri          The content URI of the audio file.
     * @param fileSize     File size in bytes (from DocumentFile.length()).
     * @param lastModified Last-modified timestamp in milliseconds (from DocumentFile.lastModified()).
     * @return Audio object with metadata populated from the URI.
     */
    public static Audio loadFromUri(Context context, Uri uri, long fileSize, long lastModified) {
        MediaMetadataLoader loader = new MediaMetadataLoader(context, uri, fileSize, lastModified);
        return loader.createAudio();
    }
    
    /**
     * Populate an existing Audio object with metadata from the file.
     * This method is provided for backward compatibility.
     *
     * @deprecated Use {@link #loadFromFile(File)} instead.
     */
    @Deprecated
    public static void populateAudio(File file, Audio audio) {
        MediaMetadataLoader loader = new MediaMetadataLoader(file);
        loader.setAudioMetadata(audio);
    }
    
    private Audio createAudio() {
        Audio audio = new Audio();
        setAudioMetadata(audio);
        return audio;
    }
    
    /**
     * @noinspection DataFlowIssue
     */
    private void setAudioMetadata(Audio audio) {
        audio.setTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
        
        if (file != null) {
            audio.setName(file.getName());
            audio.setPath(file.getAbsolutePath());
            audio.setSize(file.length());
            audio.setDateModified(file.lastModified());
        } else {
            // SAF path — use the URI string as the path identifier so we can
            // reconstruct the URI later for playback and cover art loading.
            String uriString = contentUri != null ? contentUri.toString() : "";
            // Extract just the filename from the document URI if we can.
            String displayName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (displayName == null || displayName.isEmpty()) {
                // Fall back to the last path segment as a reasonable display name.
                displayName = contentUri != null && contentUri.getLastPathSegment() != null
                        ? contentUri.getLastPathSegment()
                        : uriString;
            }
            audio.setName(displayName);
            audio.setPath(uriString);
            audio.setSize(cachedSize);
            audio.setDateModified(cachedLastModified);
        }

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

        audio.setDateAdded(System.currentTimeMillis());
        audio.setHash(MetaDataHelper.INSTANCE.generateStableHash(audio));
    }
}
