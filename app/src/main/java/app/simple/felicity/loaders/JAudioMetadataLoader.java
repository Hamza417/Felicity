package app.simple.felicity.loaders;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;

import app.simple.felicity.models.normal.Audio;

public class JAudioMetadataLoader {
    
    private final File file;
    private final AudioFile audioFile;
    
    public JAudioMetadataLoader(File file) throws CannotReadException, TagException, InvalidAudioFrameException, ReadOnlyFileException, IOException {
        this.file = file;
        this.audioFile = AudioFileIO.read(file);
    }
    
    public JAudioMetadataLoader(String path) throws CannotReadException, TagException, InvalidAudioFrameException, ReadOnlyFileException, IOException {
        this(new File(path));
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
        audio.setSamplingRate(getSamplingRate());
        audio.setBitPerSample(getBitPerSample());
    }
    
    private String getAudioFileTag(FieldKey tag) {
        return audioFile.getTag().getFirst(tag);
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
        return audioFile.getAudioHeader().getBitRateAsNumber();
    }
    
    private long getDuration() {
        return audioFile.getAudioHeader().getTrackLength();
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
        return audioFile.getAudioHeader().getBitsPerSample();
    }
}
