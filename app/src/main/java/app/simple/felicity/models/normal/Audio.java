package app.simple.felicity.models.normal;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "audio")
public class Audio implements Parcelable {
    
    @ColumnInfo (name = "name")
    private String name;
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
    private int size;
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
        dest.writeInt(size);
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
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public void setYear(@Nullable String year) {
        this.year = year;
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
    
    public String getCompilation() {
        return compilation;
    }
    
    public void setCompilation(String compilation) {
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
    
    public String getWriter() {
        return writer;
    }
    
    public void setWriter(String writer) {
        this.writer = writer;
    }
}
