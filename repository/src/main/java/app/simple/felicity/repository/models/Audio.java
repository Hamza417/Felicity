package app.simple.felicity.repository.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a single audio track stored in the local library database.
 *
 * <p>The {@code id} field is the auto-incremented primary key assigned by Room on insertion.
 * The {@code hash} field holds the XXHash64 content fingerprint — the same song at two
 * different locations will have the same hash, which is intentional: it lets song statistics
 * stay connected to a track even when the file is moved or temporarily removed from the
 * device. The {@code path} column carries a unique index because no two library entries
 * should ever point at the same file system location.</p>
 *
 * @author Hamza417
 * @noinspection EqualsReplaceableByObjectsCall
 */
@Entity (
        tableName = "audio",
        indices = {
                @Index (value = {"hash"}),
                @Index (value = {"uri"}, unique = true)
        }
)
public class Audio implements Parcelable {
    
    public static final int AUDIO_QUALITY_LQ = 0;
    public static final int AUDIO_QUALITY_MQ = 1;
    public static final int AUDIO_QUALITY_HQ = 2;
    public static final int AUDIO_QUALITY_LOSSLESS = 3;
    public static final int AUDIO_QUALITY_HI_RES = 4;
    
    public static final String NOT_AVAILABLE = "N/A";
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
    @PrimaryKey (autoGenerate = true)
    private long id;
    @ColumnInfo (name = "hash")
    private long hash;
    @ColumnInfo (name = "title")
    @Nullable
    private String title;
    @ColumnInfo (name = "artist")
    @Nullable
    private String artist;
    @ColumnInfo (name = "uri")
    private String uri;
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
    
    /**
     * Bitrate in kbps
     */
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
    // TODO -> rename to "sample_rate" for consistency
    private long sampleRate;
    @ColumnInfo (name = "bit_per_sample")
    private long bitPerSample;
    @ColumnInfo (name = "writer")
    @Nullable
    private String writer;
    @ColumnInfo (name = "is_available", defaultValue = "1")
    private boolean isAvailable = true;
    @ColumnInfo (name = "is_favorite", defaultValue = "0")
    private boolean isFavorite = false;
    @ColumnInfo (name = "always_skip", defaultValue = "0")
    private boolean alwaysSkip = false;
    /**
     * The real filesystem path to this audio file, resolved from MediaStore
     * (e.g. "/storage/emulated/0/Music/song.mp3"). This is what we use for folder
     * hierarchy since the SAF content URI looks like "content://..." and has no
     * meaningful path segments. May be null if MediaStore hasn't seen the file yet.
     */
    @ColumnInfo (name = "path")
    @Nullable
    private String path;
    
    /**
     * Per-track ReplayGain loudness offset as a raw tag string, e.g. "+5.32 dB".
     * Null when the file has no REPLAYGAIN_TRACK_GAIN tag embedded.
     */
    @ColumnInfo (name = "replay_gain_track_gain")
    @Nullable
    private String replayGainTrackGain;
    
    /**
     * Per-track ReplayGain peak sample value, e.g. "0.987654".
     * Null when the file has no REPLAYGAIN_TRACK_PEAK tag embedded.
     */
    @ColumnInfo (name = "replay_gain_track_peak")
    @Nullable
    private String replayGainTrackPeak;
    
    /**
     * Album-level ReplayGain loudness offset, e.g. "+3.22 dB".
     * Null when the file has no REPLAYGAIN_ALBUM_GAIN tag embedded.
     */
    @ColumnInfo (name = "replay_gain_album_gain")
    @Nullable
    private String replayGainAlbumGain;
    
    /**
     * Album-level ReplayGain peak sample value, e.g. "0.995432".
     * Null when the file has no REPLAYGAIN_ALBUM_PEAK tag embedded.
     */
    @ColumnInfo (name = "replay_gain_album_peak")
    @Nullable
    private String replayGainAlbumPeak;
    
    public Audio() {
    }
    
    protected Audio(Parcel in) {
        name = in.readString();
        id = in.readLong();
        hash = in.readLong();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        uri = in.readString();
        track = in.readInt();
        author = in.readString();
        size = in.readLong();
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
        isFavorite = in.readByte() != 0;
        alwaysSkip = in.readByte() != 0;
        path = in.readString();
        replayGainTrackGain = in.readString();
        replayGainTrackPeak = in.readString();
        replayGainAlbumGain = in.readString();
        replayGainAlbumPeak = in.readString();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeLong(id);
        dest.writeLong(hash);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(uri);
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
        dest.writeByte((byte) (isFavorite ? 1 : 0));
        dest.writeByte((byte) (alwaysSkip ? 1 : 0));
        dest.writeString(path);
        dest.writeString(replayGainTrackGain);
        dest.writeString(replayGainTrackPeak);
        dest.writeString(replayGainAlbumGain);
        dest.writeString(replayGainAlbumPeak);
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
        return title;
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
    
    public String getUri() {
        return uri;
    }
    
    public void setUri(String uri) {
        this.uri = uri;
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getHash() {
        return hash;
    }
    
    public void setHash(long hash) {
        this.hash = hash;
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
    
    /**
     * Bitrate in kbps. E.g. 320 for a 320 kbps MP3, 1411 for CD-quality lossless, etc.
     *
     * @return the bitrate of this audio file in kilobits per second, or 0 if unknown.
     */
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
    
    public long getSampleRate() {
        return sampleRate;
    }
    
    public void setSampleRate(long sampleRate) {
        this.sampleRate = sampleRate;
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
    
    public boolean isFavorite() {
        return isFavorite;
    }
    
    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
    
    public boolean isAlwaysSkip() {
        return alwaysSkip;
    }
    
    public void setAlwaysSkip(boolean alwaysSkip) {
        this.alwaysSkip = alwaysSkip;
    }
    
    /**
     * Returns the real filesystem path resolved from MediaStore, or null if it hasn't
     * been populated yet. Use this for folder hierarchy browsing instead of the SAF URI.
     */
    @Nullable
    public String getPath() {
        return path;
    }
    
    /**
     * Sets the real filesystem path for this audio file. Normally populated by
     * {@code MediaStorePaths} during the scan, not something you'd set manually.
     */
    public void setPath(@Nullable String path) {
        this.path = path;
    }
    
    @Nullable
    public String getReplayGainTrackGain() {
        return replayGainTrackGain;
    }
    
    public void setReplayGainTrackGain(@Nullable String replayGainTrackGain) {
        this.replayGainTrackGain = replayGainTrackGain;
    }
    
    @Nullable
    public String getReplayGainTrackPeak() {
        return replayGainTrackPeak;
    }
    
    public void setReplayGainTrackPeak(@Nullable String replayGainTrackPeak) {
        this.replayGainTrackPeak = replayGainTrackPeak;
    }
    
    @Nullable
    public String getReplayGainAlbumGain() {
        return replayGainAlbumGain;
    }
    
    public void setReplayGainAlbumGain(@Nullable String replayGainAlbumGain) {
        this.replayGainAlbumGain = replayGainAlbumGain;
    }
    
    @Nullable
    public String getReplayGainAlbumPeak() {
        return replayGainAlbumPeak;
    }
    
    public void setReplayGainAlbumPeak(@Nullable String replayGainAlbumPeak) {
        this.replayGainAlbumPeak = replayGainAlbumPeak;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "Audio{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", hash=" + hash +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", path='" + uri + '\'' +
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
                ", samplingRate=" + sampleRate +
                ", bitPerSample=" + bitPerSample +
                ", writer='" + writer + '\'' +
                ", isAvailable=" + isAvailable +
                ", isFavorite=" + isFavorite +
                ", alwaysSkip=" + alwaysSkip +
                ", path='" + path + '\'' +
                ", replayGainTrackGain='" + replayGainTrackGain + '\'' +
                ", replayGainTrackPeak='" + replayGainTrackPeak + '\'' +
                ", replayGainAlbumGain='" + replayGainAlbumGain + '\'' +
                ", replayGainAlbumPeak='" + replayGainAlbumPeak + '\'' +
                '}';
    }
    
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
        if (hash != audio.hash) {
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
        if (sampleRate != audio.sampleRate) {
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
        if (uri != null ? !uri.equals(audio.uri) : audio.uri != null) {
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
        if (isFavorite != audio.isFavorite) {
            return false;
        }
        if (alwaysSkip != audio.alwaysSkip) {
            return false;
        }
        return writer != null ? writer.equals(audio.writer) : audio.writer == null;
    }
    
    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + Long.hashCode(id);
        result = 31 * result + Long.hashCode(hash);
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (artist != null ? artist.hashCode() : 0);
        result = 31 * result + (album != null ? album.hashCode() : 0);
        result = 31 * result + (uri != null ? uri.hashCode() : 0);
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
        result = 31 * result + Long.hashCode(sampleRate);
        result = 31 * result + Long.hashCode(bitPerSample);
        result = 31 * result + (writer != null ? writer.hashCode() : 0);
        result = 31 * result + (isAvailable ? 1 : 0);
        result = 31 * result + (isFavorite ? 1 : 0);
        result = 31 * result + (alwaysSkip ? 1 : 0);
        return result;
    }
    
    public Audio copy() {
        Audio audio = new Audio();
        audio.setName(getName());
        audio.setId(getId());
        audio.setHash(getHash());
        audio.setTitle(getTitle());
        audio.setArtist(getArtist());
        audio.setAlbum(getAlbum());
        audio.setUri(getUri());
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
        audio.setSampleRate(getSampleRate());
        audio.setBitPerSample(getBitPerSample());
        audio.setWriter(getWriter());
        audio.setFavorite(isFavorite());
        audio.setAlwaysSkip(isAlwaysSkip());
        audio.setPath(getPath());
        audio.setReplayGainTrackGain(getReplayGainTrackGain());
        audio.setReplayGainTrackPeak(getReplayGainTrackPeak());
        audio.setReplayGainAlbumGain(getReplayGainAlbumGain());
        audio.setReplayGainAlbumPeak(getReplayGainAlbumPeak());
        return audio;
    }
    
    /**
     * Determines the specific audio quality classification of a track.
     * <p>
     * This evaluation uses rigorous thresholds to categorize streams, ensuring that
     * high-fidelity files (including ultra-high sample rates up to 384kHz, 768kHz, and beyond)
     * are correctly flagged for downstream processing.
     * <p>
     * Quality parameters:
     * - Hi-Res: Uncompressed/Lossless with bit depth >= 24-bit OR sample rate > 48,000 Hz.
     * - Lossless: Uncompressed/Lossless matching standard CD quality (16-bit, up to 48kHz).
     * - High Quality (HQ): Lossy streams with a bitrate >= 320 kbps.
     * - Medium Quality (MQ): Lossy streams with a bitrate >= 192 kbps.
     * - Low Quality (LQ): Lossy streams falling below 192 kbps.
     *
     * @return An integer constant representing the evaluated audio quality.
     */
    public int getAudioQuality() {
        // Sanitize MIME type for reliable matching
        String mt = mimeType != null ? mimeType.toLowerCase() : "";
        
        // Determine if the track meets the objective mathematical threshold for High-Resolution audio.
        // The JAS (Japan Audio Society) specification defines Hi-Res as anything strictly exceeding
        // standard CD specifications (16-bit depth / 48kHz sample rate).
        // Using a > 48000 check guarantees coverage for all extreme sample rates without upper bounds.
        boolean isHiRes = (bitPerSample >= 24) || (sampleRate > 48000);
        
        // Explicit Lossless/Uncompressed Check
        // Covers standard lossless codecs (FLAC, ALAC, APE), uncompressed PCM (WAV, AIFF),
        // and SACD formats (DSD/DSF) which utilize extreme sample frequencies.
        boolean isLosslessFormat = mt.contains("flac")
                || mt.contains("wav")
                || mt.contains("alac")
                || mt.contains("aiff")
                || mt.contains("ape")
                || mt.contains("dsf")
                || mt.contains("dsd");
        
        if (isLosslessFormat) {
            return isHiRes ? AUDIO_QUALITY_HI_RES : AUDIO_QUALITY_LOSSLESS;
        }
        
        // Fallback Heuristics for Missing or Generic MIME Types
        // If a track lacks clear metadata but pushes a massive bitrate (>= 1000 kbps),
        // it is mathematically guaranteed to be a lossless file rather than a bloated lossy stream.
        if (bitrate >= 1000) {
            return isHiRes ? AUDIO_QUALITY_HI_RES : AUDIO_QUALITY_LOSSLESS;
        }
        
        // Lossy Format Classification (MP3, AAC, OGG, Opus)
        // Lossy files are evaluated purely by their compression threshold (kbps).
        // Even if a lossy file was artificially up-sampled to 96kHz, it retains its original
        // lossy artifacts and should never be falsely elevated to Hi-Res status.
        if (bitrate >= 320) {
            return AUDIO_QUALITY_HQ;
        } else if (bitrate >= 192) {
            return AUDIO_QUALITY_MQ;
        } else {
            return AUDIO_QUALITY_LQ;
        }
    }
    
    /**
     * Convenience method to get a File object for this audio's path.
     *
     * @return a File object representing the audio file.
     */
    public File getFile() {
        return new File(uri);
    }
}