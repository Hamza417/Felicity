package app.simple.felicity.repository.metadata;

import android.util.Log;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;

import app.simple.felicity.core.utils.FileUtils;
import app.simple.felicity.repository.models.Audio;

public class JAudioMetadataLoader {
    
    private final static String TAG = "JAudioMetadataLoader";
    
    private final File file;
    private final AudioFile audioFile;
    
    private JAudioMetadataLoader(File file) throws CannotReadException, TagException, InvalidAudioFrameException, ReadOnlyFileException, IOException {
        this.file = file;
        this.audioFile = AudioFileIO.read(file);
    }
    
    /**
     * Load audio metadata from a file and return a populated Audio object.
     *
     * @param file The audio file to load metadata from
     * @return Audio object with metadata populated from the file
     * @throws CannotReadException        if the file cannot be read
     * @throws TagException               if there's an error reading tags
     * @throws InvalidAudioFrameException if the audio frame is invalid
     * @throws ReadOnlyFileException      if the file is read-only
     * @throws IOException                if there's an I/O error
     */
    public static Audio loadFromFile(File file) throws CannotReadException, TagException, InvalidAudioFrameException, ReadOnlyFileException, IOException {
        JAudioMetadataLoader loader = new JAudioMetadataLoader(file);
        return loader.createAudio();
    }
    
    /**
     * Load audio metadata from a file path and return a populated Audio object.
     *
     * @param path The path to the audio file
     * @return Audio object with metadata populated from the file
     * @throws CannotReadException        if the file cannot be read
     * @throws TagException               if there's an error reading tags
     * @throws InvalidAudioFrameException if the audio frame is invalid
     * @throws ReadOnlyFileException      if the file is read-only
     * @throws IOException                if there's an I/O error
     */
    public static Audio loadFromFile(String path) throws CannotReadException, TagException, InvalidAudioFrameException, ReadOnlyFileException, IOException {
        return loadFromFile(new File(path));
    }
    
    /**
     * Populate an existing Audio object with metadata from the file.
     * This method is provided for backward compatibility.
     *
     * @deprecated Use {@link #loadFromFile(File)} instead
     */
    @Deprecated
    public static void populateAudio(File file, Audio audio) throws CannotReadException, TagException, InvalidAudioFrameException, ReadOnlyFileException, IOException {
        JAudioMetadataLoader loader = new JAudioMetadataLoader(file);
        loader.setAudioMetadata(audio);
    }
    
    private Audio createAudio() {
        Audio audio = new Audio();
        setAudioMetadata(audio);
        return audio;
    }
    
    private void setAudioMetadata(Audio audio) {
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
        audio.setSamplingRate(getSamplingRate());
        audio.setBitPerSample(getBitPerSample());
        audio.setId(generateId());
        audio.setSize(file.length());
        audio.setDateModified(file.lastModified());
        audio.setDateAdded(System.currentTimeMillis());
    }
    
    private String getAudioFileTag(FieldKey tag) {
        try {
            return audioFile.getTag().getFirst(tag);
        } catch (Exception e) {
            return "";
        }
    }
    
    private String getTitle() {
        return getAudioFileTag(FieldKey.TITLE);
    }
    
    private String getArtist() {
        return getAudioFileTag(FieldKey.ARTIST);
    }
    
    private String getAlbum() {
        return getAudioFileTag(FieldKey.ALBUM);
    }
    
    private String getGenre() {
        return getAudioFileTag(FieldKey.GENRE);
    }
    
    private String getYear() {
        return getAudioFileTag(FieldKey.YEAR);
    }
    
    private String getComposer() {
        return getAudioFileTag(FieldKey.COMPOSER);
    }
    
    private String getAlbumArtist() {
        return getAudioFileTag(FieldKey.ALBUM_ARTIST);
    }
    
    private String getWriter() {
        return getAudioFileTag(FieldKey.LYRICIST);
    }
    
    private String getCompilation() {
        return getAudioFileTag(FieldKey.IS_COMPILATION);
    }
    
    private String getDate() {
        return getAudioFileTag(FieldKey.YEAR);
    }
    
    private long getBitrate() {
        Log.d(TAG, "Bitrate: " + audioFile.getAudioHeader().getBitRateAsNumber() + " for file: " + file.getAbsolutePath());
        return audioFile.getAudioHeader().getBitRateAsNumber();
    }
    
    private long getDuration() {
        // Get precise track length in seconds (can be a decimal)
        // in format 30.450 which means 30 seconds and 450 milliseconds
        double length = audioFile.getAudioHeader().getPreciseTrackLength();
        // Convert to exact milliseconds
        return (long) (length * 1000);
    }
    
    private String getNumTracks() {
        return getAudioFileTag(FieldKey.TRACK_TOTAL);
    }
    
    private String getDiscNumber() {
        return getAudioFileTag(FieldKey.DISC_NO);
    }
    
    private String getTrackNumber() {
        return getAudioFileTag(FieldKey.TRACK);
    }
    
    private String getMIMEType() {
        return audioFile.getExt();
    }
    
    private long getSamplingRate() {
        return audioFile.getAudioHeader().getSampleRateAsNumber();
    }
    
    private long getBitPerSample() {
        Log.d("JAudioMetadataLoader", "Bits per sample: " + audioFile.getAudioHeader().getBitsPerSample());
        return audioFile.getAudioHeader().getBitsPerSample();
    }
    
    private long generateId() {
        return FileUtils.INSTANCE.generateXXHash64(file, Integer.MAX_VALUE);
    }
}
