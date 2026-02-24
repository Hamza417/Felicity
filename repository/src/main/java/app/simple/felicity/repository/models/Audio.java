package app.simple.felicity.repository.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "audio")
public class Audio implements Parcelable {
    
    public static final int AUDIO_QUALITY_LQ = 0;
    public static final int AUDIO_QUALITY_MQ = 1;
    public static final int AUDIO_QUALITY_HQ = 2;
    public static final int AUDIO_QUALITY_LOSSLESS = 3;
    public static final int AUDIO_QUALITY_HI_RES = 4;
    
    public static final Creator <Audio> CREATOR = new Creator <>() {
        @Override
        public Audio createFromParcel(Parcel in) {
            return new Audio(in);
        }
        
        @Override
        public Audio[] newArray(int size) {
            return new Audio[size];
        }
    };
    @ColumnInfo (name = "name")
    private String name;
    @ColumnInfo (name = "id")
    @PrimaryKey
    private long id;
    @ColumnInfo (name = "title")
    @Nullable
    private String title;
    @ColumnInfo (name = "artist")
    @Nullable
    private String artist;
    @ColumnInfo (name = "path")
    private String path;
    @ColumnInfo (name = "track")
    private int track;
    @ColumnInfo (name = "album")
    @Nullable
    private String album;
    @ColumnInfo (name = "size")
    private long size;
    @ColumnInfo (name = "author")
    @Nullable
    private String author;
    @ColumnInfo (name = "album_artist")
    @Nullable
    private String albumArtist;
    @ColumnInfo (name = "year")
    @Nullable
    private String year;
    @ColumnInfo (name = "bitrate")
    private long bitrate;
    @ColumnInfo (name = "duration")
    private long duration;
    @ColumnInfo (name = "composer")
    @Nullable
    private String composer;
    @ColumnInfo (name = "date")
    @Nullable
    private String date;
    @ColumnInfo (name = "disc_number")
    @Nullable
    private String discNumber;
    @ColumnInfo (name = "genre")
    @Nullable
    private String genre;
    @ColumnInfo (name = "date_added")
    private long dateAdded;
    @ColumnInfo (name = "date_modified")
    private long dateModified;
    @ColumnInfo (name = "date_taken")
    private long dateTaken;
    @ColumnInfo (name = "album_id")
    private long albumId;
    @ColumnInfo (name = "track_number")
    @Nullable
    private String trackNumber;
    @ColumnInfo (name = "compilation")
    @Nullable
    private String compilation;
    @ColumnInfo (name = "mimeType")
    private String mimeType;
    @ColumnInfo (name = "num_tracks")
    private String numTracks;
    @ColumnInfo (name = "sampling_rate")
    private long samplingRate;
    @ColumnInfo (name = "bit_per_sample")
    private long bitPerSample;
    @ColumnInfo (name = "writer")
    @Nullable
    private String writer;
    @ColumnInfo (name = "is_available", defaultValue = "1")
    private boolean isAvailable = true;
    
    public Audio() {
    }
    
    protected Audio(Parcel in) {
        name = in.readString();
        id = in.readLong();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        path = in.readString();
        track = in.readInt();
        author = in.readString();
        size = in.readInt();
        albumArtist = in.readString();
        year = in.readString();
        bitrate = in.readLong();
        composer = in.readString();
        duration = in.readLong();
        date = in.readString();
        discNumber = in.readString();
        genre = in.readString();
        trackNumber = in.readString();
        dateAdded = in.readLong();
        dateModified = in.readLong();
        dateTaken = in.readLong();
        albumId = in.readLong();
        isAvailable = in.readByte() != 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(path);
        dest.writeInt(track);
        dest.writeString(author);
        dest.writeLong(size);
        dest.writeString(albumArtist);
        dest.writeString(year);
        dest.writeLong(bitrate);
        dest.writeString(composer);
        dest.writeLong(duration);
        dest.writeString(date);
        dest.writeString(discNumber);
        dest.writeString(genre);
        dest.writeString(trackNumber);
        dest.writeLong(dateAdded);
        dest.writeLong(dateModified);
        dest.writeLong(dateTaken);
        dest.writeLong(albumId);
        dest.writeByte((byte) (isAvailable ? 1 : 0));
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    @Nullable
    public String getTitle() {
        if (title == null || title.isEmpty()) {
            return getName();
        } else {
            return title;
        }
    }
    
    public void setTitle(@Nullable String title) {
        this.title = title;
    }
    
    @Nullable
    public String getArtist() {
        return artist;
    }
    
    public void setArtist(@Nullable String artist) {
        this.artist = artist;
    }
    
    @Nullable
    public String getAlbum() {
        return album;
    }
    
    public void setAlbum(@Nullable String album) {
        this.album = album;
    }
    
    public long getAlbumId() {
        return albumId;
    }
    
    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getDateAdded() {
        return dateAdded;
    }
    
    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }
    
    public long getDateModified() {
        return dateModified;
    }
    
    public void setDateModified(long dateModified) {
        this.dateModified = dateModified;
    }
    
    public long getDateTaken() {
        return dateTaken;
    }
    
    public void setDateTaken(long dateTaken) {
        this.dateTaken = dateTaken;
    }
    
    public int getTrack() {
        return track;
    }
    
    public void setTrack(int track) {
        this.track = track;
    }
    
    @Nullable
    public String getYear() {
        return year;
    }
    
    public void setYear(@Nullable String year) {
        this.year = year;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    public long getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(long bitrate) {
        this.bitrate = bitrate;
    }
    
    @Nullable
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(@Nullable String author) {
        this.author = author;
    }
    
    @Nullable
    public String getAlbumArtist() {
        return albumArtist;
    }
    
    public void setAlbumArtist(@Nullable String albumArtist) {
        this.albumArtist = albumArtist;
    }
    
    @Nullable
    public String getComposer() {
        return composer;
    }
    
    public void setComposer(@Nullable String composer) {
        this.composer = composer;
    }
    
    @Nullable
    public String getDate() {
        return date;
    }
    
    public void setDate(@Nullable String date) {
        this.date = date;
    }
    
    @Nullable
    public String getDiscNumber() {
        return discNumber;
    }
    
    public void setDiscNumber(@Nullable String discNumber) {
        this.discNumber = discNumber;
    }
    
    @Nullable
    public String getGenre() {
        return genre;
    }
    
    public void setGenre(@Nullable String genre) {
        this.genre = genre;
    }
    
    @Nullable
    public String getTrackNumber() {
        return trackNumber;
    }
    
    public void setTrackNumber(@Nullable String trackNumber) {
        this.trackNumber = trackNumber;
    }
    
    @Nullable
    public String getCompilation() {
        return compilation;
    }
    
    public void setCompilation(@Nullable String compilation) {
        this.compilation = compilation;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    public String getNumTracks() {
        return numTracks;
    }
    
    public void setNumTracks(String numTracks) {
        this.numTracks = numTracks;
    }
    
    public long getSamplingRate() {
        return samplingRate;
    }
    
    public void setSamplingRate(long samplingRate) {
        this.samplingRate = samplingRate;
    }
    
    public long getBitPerSample() {
        return bitPerSample;
    }
    
    public void setBitPerSample(long bitPerSample) {
        this.bitPerSample = bitPerSample;
    }
    
    @Nullable
    public String getWriter() {
        return writer;
    }
    
    public void setWriter(@Nullable String writer) {
        this.writer = writer;
    }
    
    public boolean isAvailable() {
        return isAvailable;
    }
    
    public void setAvailable(boolean available) {
        isAvailable = available;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "Audio{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", path='" + path + '\'' +
                ", track=" + track +
                ", author='" + author + '\'' +
                ", size=" + size +
                ", albumArtist='" + albumArtist + '\'' +
                ", year='" + year + '\'' +
                ", bitrate=" + bitrate +
                ", duration=" + duration +
                ", composer='" + composer + '\'' +
                ", date='" + date + '\'' +
                ", discNumber='" + discNumber + '\'' +
                ", genre='" + genre + '\'' +
                ", dateAdded=" + dateAdded +
                ", dateModified=" + dateModified +
                ", dateTaken=" + dateTaken +
                ", albumId=" + albumId +
                ", trackNumber='" + trackNumber + '\'' +
                ", compilation='" + compilation + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", numTracks='" + numTracks + '\'' +
                ", samplingRate=" + samplingRate +
                ", bitPerSample=" + bitPerSample +
                ", writer='" + writer + '\'' +
                ", isAvailable=" + isAvailable +
                '}';
    }
    
    /**
     * @noinspection EqualsReplaceableByObjectsCall
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        Audio audio = (Audio) obj;
        
        if (id != audio.id) {
            return false;
        }
        if (track != audio.track) {
            return false;
        }
        if (size != audio.size) {
            return false;
        }
        if (bitrate != audio.bitrate) {
            return false;
        }
        if (duration != audio.duration) {
            return false;
        }
        if (dateAdded != audio.dateAdded) {
            return false;
        }
        if (dateModified != audio.dateModified) {
            return false;
        }
        if (dateTaken != audio.dateTaken) {
            return false;
        }
        if (albumId != audio.albumId) {
            return false;
        }
        if (samplingRate != audio.samplingRate) {
            return false;
        }
        if (bitPerSample != audio.bitPerSample) {
            return false;
        }
        if (name != null ? !name.equals(audio.name) : audio.name != null) {
            return false;
        }
        if (title != null ? !title.equals(audio.title) : audio.title != null) {
            return false;
        }
        if (artist != null ? !artist.equals(audio.artist) : audio.artist != null) {
            return false;
        }
        if (album != null ? !album.equals(audio.album) : audio.album != null) {
            return false;
        }
        if (path != null ? !path.equals(audio.path) : audio.path != null) {
            return false;
        }
        if (author != null ? !author.equals(audio.author) : audio.author != null) {
            return false;
        }
        if (albumArtist != null ? !albumArtist.equals(audio.albumArtist) : audio.albumArtist != null) {
            return false;
        }
        if (year != null ? !year.equals(audio.year) : audio.year != null) {
            return false;
        }
        if (composer != null ? !composer.equals(audio.composer) : audio.composer != null) {
            return false;
        }
        if (date != null ? !date.equals(audio.date) : audio.date != null) {
            return false;
        }
        if (discNumber != null ? !discNumber.equals(audio.discNumber) : audio.discNumber != null) {
            return false;
        }
        if (genre != null ? !genre.equals(audio.genre) : audio.genre != null) {
            return false;
        }
        if (trackNumber != null ? !trackNumber.equals(audio.trackNumber) : audio.trackNumber != null) {
            return false;
        }
        if (compilation != null ? !compilation
                .equals(audio.compilation) : audio.compilation != null) {
            return false;
        }
        if (mimeType != null ? !mimeType.equals(audio.mimeType) : audio.mimeType != null) {
            return false;
        }
        if (numTracks != null ? !numTracks.equals(audio.numTracks) : audio.numTracks != null) {
            return false;
        }
        if (isAvailable != audio.isAvailable) {
            return false;
        }
        return writer != null ? writer.equals(audio.writer) : audio.writer == null;
    }
    
    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + Long.hashCode(id);
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (artist != null ? artist.hashCode() : 0);
        result = 31 * result + (album != null ? album.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + track;
        result = 31 * result + (author != null ? author.hashCode() : 0);
        result = 31 * result + Long.hashCode(size);
        result = 31 * result + (albumArtist != null ? albumArtist.hashCode() : 0);
        result = 31 * result + (year != null ? year.hashCode() : 0);
        result = 31 * result + Long.hashCode(bitrate);
        result = 31 * result + Long.hashCode(duration);
        result = 31 * result + (composer != null ? composer.hashCode() : 0);
        result = 31 * result + (date != null ? date.hashCode() : 0);
        result = 31 * result + (discNumber != null ? discNumber.hashCode() : 0);
        result = 31 * result + (genre != null ? genre.hashCode() : 0);
        result = 31 * result + Long.hashCode(dateAdded);
        result = 31 * result + Long.hashCode(dateModified);
        result = 31 * result + Long.hashCode(dateTaken);
        result = 31 * result + Long.hashCode(albumId);
        result = 31 * result + (trackNumber != null ? trackNumber.hashCode() : 0);
        result = 31 * result + (compilation != null ? compilation.hashCode() : 0);
        result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
        result = 31 * result + (numTracks != null ? numTracks.hashCode() :
                0);
        result = 31 * result + Long.hashCode(samplingRate);
        result = 31 * result + Long.hashCode(bitPerSample);
        result = 31 * result + (writer != null ? writer.hashCode() : 0);
        result = 31 * result + (isAvailable ? 1 : 0);
        return result;
    }
    
    public Audio copy() {
        Audio audio = new Audio();
        audio.setName(getName());
        audio.setId(getId());
        audio.setTitle(getTitle());
        audio.setArtist(getArtist());
        audio.setAlbum(getAlbum());
        audio.setPath(getPath());
        audio.setTrack(getTrack());
        audio.setAuthor(getAuthor());
        audio.setSize(getSize());
        audio.setAlbumArtist(getAlbumArtist());
        audio.setYear(getYear());
        audio.setBitrate(getBitrate());
        audio.setDuration(getDuration());
        audio.setComposer(getComposer());
        audio.setDate(getDate());
        audio.setDiscNumber(getDiscNumber());
        audio.setGenre(getGenre());
        audio.setDateAdded(getDateAdded());
        audio.setDateModified(getDateModified());
        audio.setDateTaken(getDateTaken());
        audio.setAlbumId(getAlbumId());
        audio.setTrackNumber(getTrackNumber());
        audio.setCompilation(getCompilation());
        audio.setMimeType(getMimeType());
        audio.setNumTracks(getNumTracks());
        audio.setSamplingRate(getSamplingRate());
        audio.setBitPerSample(getBitPerSample());
        audio.setWriter(getWriter());
        return audio;
    }
    
    /**
     * Determine an approximate audio quality class for this track.
     * <p>
     * Heuristics used:
     * - FLAC: use bit depth (bitPerSample) -> 24+ is Hi-Res, otherwise Lossless.
     * - MP3/MPEG: use bitrate thresholds (bps) -> 320k+ HQ, 192k+ MQ, otherwise LQ.
     * - Other formats: fallback to bitrate thresholds.
     * <p>
     * Returns one of AUDIO_QUALITY_LQ, AUDIO_QUALITY_MQ, AUDIO_QUALITY_HQ,
     * AUDIO_QUALITY_LOSSLESS or AUDIO_QUALITY_HI_RES.
     */
    public int getAudioQuality() {
        String mt = mimeType != null ? mimeType.toLowerCase() : "";
        if (mt.contains("flac")) {
            // FLAC: 16-bit considered Lossless, 24-bit considered Hi-Res
            return bitPerSample >= 24 ? AUDIO_QUALITY_HI_RES : AUDIO_QUALITY_LOSSLESS;
        } else if (mt.contains("mp3") || mt.contains("mpeg")) {
            // MP3: 320 kbps -> HQ, 192 kbps -> Standard (MQ), lower -> LQ
            if (bitrate >= 320) {
                return AUDIO_QUALITY_HQ;
            } else if (bitrate >= 192) {
                return AUDIO_QUALITY_MQ;
            } else {
                return AUDIO_QUALITY_LQ;
            }
        } else {
            // Fallback to bitrate-based classification
            if (bitrate >= 320) {
                return AUDIO_QUALITY_HQ;
            } else if (bitrate >= 192) {
                return AUDIO_QUALITY_MQ;
            } else {
                // Fallback to bitrate-based classification
                return AUDIO_QUALITY_LQ;
            }
        }
    }
    
    /**
     * Convenience method to get a File object for this audio's path.
     *
     * @return a File object representing the audio file.
     */
    public File getFile() {
        return new File(path);
    }
}