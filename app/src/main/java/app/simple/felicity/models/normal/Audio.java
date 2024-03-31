package app.simple.felicity.models.normal;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "audio")
public class Audio implements Parcelable {
    
    @ColumnInfo (name = "name")
    private String name;
    
    @ColumnInfo (name = "title")
    private String title;
    
    @ColumnInfo (name = "artist")
    private String artist;
    
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
    
    @ColumnInfo (name = "album")
    private String album;
    @ColumnInfo (name = "author")
    private String author;
    
    @ColumnInfo (name = "art_uri")
    private String artUri;
    
    @ColumnInfo (name = "file_uri")
    private String fileUri;
    
    @ColumnInfo (name = "path")
    private String path;
    
    @ColumnInfo (name = "mime_type")
    private String mimeType;
    
    @ColumnInfo (name = "track")
    private int track;
    @ColumnInfo (name = "album_artist")
    private String albumArtist;
    
    @ColumnInfo (name = "size")
    private int size;
    @ColumnInfo (name = "year")
    private String year;
    @ColumnInfo (name = "bitrate")
    private String bitrate;
    @ColumnInfo (name = "compilation")
    private String compilation;
    @ColumnInfo (name = "composer")
    private String composer;
    @ColumnInfo (name = "date")
    private String date;
    
    @ColumnInfo (name = "duration")
    private long duration;
    @ColumnInfo (name = "disc_number")
    private String discNumber;
    @ColumnInfo (name = "genre")
    private String genre;
    @ColumnInfo (name = "has_audio")
    private String hasAudio;
    @ColumnInfo (name = "has_video")
    private String hasVideo;
    @ColumnInfo (name = "num_tracks")
    private String numTracks;
    @ColumnInfo (name = "track_number")
    private String trackNumber;
    @ColumnInfo (name = "writer")
    private String writer;
    @ColumnInfo (name = "sampling_rate")
    private String samplingRate;
    
    @ColumnInfo (name = "id")
    @PrimaryKey
    private long id;
    
    @ColumnInfo (name = "date_added")
    private long dateAdded;
    
    @ColumnInfo (name = "date_modified")
    private long dateModified;
    
    @ColumnInfo (name = "date_taken")
    private long dateTaken;
    
    public Audio() {
    }
    
    @ColumnInfo (name = "album_id")
    private long albumId;
    @ColumnInfo (name = "bit_per_sample")
    private String bitPerSample;
    
    protected Audio(Parcel in) {
        name = in.readString();
        title = in.readString();
        artist = in.readString();
        author = in.readString();
        album = in.readString();
        albumArtist = in.readString();
        artUri = in.readString();
        fileUri = in.readString();
        path = in.readString();
        mimeType = in.readString();
        track = in.readInt();
        year = in.readString();
        size = in.readInt();
        bitrate = in.readString();
        compilation = in.readString();
        composer = in.readString();
        date = in.readString();
        discNumber = in.readString();
        duration = in.readLong();
        genre = in.readString();
        hasAudio = in.readString();
        hasVideo = in.readString();
        numTracks = in.readString();
        trackNumber = in.readString();
        writer = in.readString();
        samplingRate = in.readString();
        bitPerSample = in.readString();
        id = in.readLong();
        dateAdded = in.readLong();
        dateModified = in.readLong();
        dateTaken = in.readLong();
        albumId = in.readLong();
    }
    
    @SuppressLint ("Range")
    public Audio(Cursor cursor) {
        int columnIndex;
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
        if (columnIndex != -1) {
            this.id = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
        if (columnIndex != -1) {
            this.name = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
        if (columnIndex != -1) {
            this.title = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        if (columnIndex != -1) {
            this.artist = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        if (columnIndex != -1) {
            this.album = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
        if (columnIndex != -1) {
            this.albumId = cursor.getLong(columnIndex);
        }
        
        this.artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), String.valueOf(albumId)).toString();
        this.fileUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)).toString();
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
        if (columnIndex != -1) {
            this.path = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
        if (columnIndex != -1) {
            this.mimeType = cursor.getString(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
        if (columnIndex != -1) {
            this.track = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
        if (columnIndex != -1) {
            this.year = String.valueOf(cursor.getInt(columnIndex));
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
        if (columnIndex != -1) {
            this.size = cursor.getInt(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE);
        if (columnIndex != -1) {
            this.bitrate = String.valueOf(cursor.getInt(columnIndex));
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
        if (columnIndex != -1) {
            this.duration = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);
        if (columnIndex != -1) {
            this.dateAdded = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED);
        if (columnIndex != -1) {
            this.dateModified = cursor.getLong(columnIndex);
        }
        
        columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_TAKEN);
        if (columnIndex != -1) {
            this.dateTaken = cursor.getLong(columnIndex);
        }
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(author);
        dest.writeString(album);
        dest.writeString(albumArtist);
        dest.writeString(artUri);
        dest.writeString(fileUri);
        dest.writeString(path);
        dest.writeString(mimeType);
        dest.writeInt(track);
        dest.writeString(year);
        dest.writeInt(size);
        dest.writeString(bitrate);
        dest.writeString(compilation);
        dest.writeString(composer);
        dest.writeString(date);
        dest.writeString(discNumber);
        dest.writeLong(duration);
        dest.writeString(genre);
        dest.writeString(hasAudio);
        dest.writeString(hasVideo);
        dest.writeString(numTracks);
        dest.writeString(trackNumber);
        dest.writeString(writer);
        dest.writeString(samplingRate);
        dest.writeString(bitPerSample);
        dest.writeLong(id);
        dest.writeLong(dateAdded);
        dest.writeLong(dateModified);
        dest.writeLong(dateTaken);
        dest.writeLong(albumId);
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getArtist() {
        return artist;
    }
    
    public void setArtist(String artist) {
        this.artist = artist;
    }
    
    public String getAlbum() {
        return album;
    }
    
    public void setAlbum(String album) {
        this.album = album;
    }
    
    public long getAlbumId() {
        return albumId;
    }
    
    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }
    
    public String getArtUri() {
        return artUri;
    }
    
    public void setArtUri(String artUri) {
        this.artUri = artUri;
    }
    
    public String getFileUri() {
        return fileUri;
    }
    
    public void setFileUri(String fileUri) {
        this.fileUri = fileUri;
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
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    public int getTrack() {
        return track;
    }
    
    public void setTrack(int track) {
        this.track = track;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public String getYear() {
        return year;
    }
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public void setYear(String year) {
        this.year = year;
    }
    
    public String getBitrate() {
        return bitrate;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "AudioModel{" +
                "name='" + name + '\'' +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", artUri='" + artUri + '\'' +
                ", fileUri='" + fileUri + '\'' +
                ", path='" + path + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", track=" + track +
                ", year=" + year +
                ", size=" + size +
                ", duration=" + duration +
                ", id=" + id +
                ", dateAdded=" + dateAdded +
                ", dateModified=" + dateModified +
                ", dateTaken=" + dateTaken +
                '}';
    }
    
    public void setBitrate(String bitrate) {
        this.bitrate = bitrate;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        Audio audio = (Audio) o;
        
        if (getTrack() != audio.getTrack()) {
            return false;
        }
        if (getYear() != audio.getYear()) {
            return false;
        }
        if (getSize() != audio.getSize()) {
            return false;
        }
        if (getDuration() != audio.getDuration()) {
            return false;
        }
        if (getId() != audio.getId()) {
            return false;
        }
        if (getDateAdded() != audio.getDateAdded()) {
            return false;
        }
        if (getDateModified() != audio.getDateModified()) {
            return false;
        }
        if (getDateTaken() != audio.getDateTaken()) {
            return false;
        }
        if (getAlbumId() != audio.getAlbumId()) {
            return false;
        }
        if (getName() != null ? !getName().equals(audio.getName()) : audio.getName() != null) {
            return false;
        }
        if (getTitle() != null ? !getTitle().equals(audio.getTitle()) : audio.getTitle() != null) {
            return false;
        }
        if (getArtist() != null ? !getArtist().equals(audio.getArtist()) : audio.getArtist() != null) {
            return false;
        }
        if (getAuthor() != null ? !getAuthor().equals(audio.getAuthor()) : audio.getAuthor() != null) {
            return false;
        }
        if (getAlbum() != null ? !getAlbum().equals(audio.getAlbum()) : audio.getAlbum() != null) {
            return false;
        }
        if (getAlbumArtist() != null ? !getAlbumArtist().equals(audio.getAlbumArtist()) : audio.getAlbumArtist() != null) {
            return false;
        }
        if (getArtUri() != null ? !getArtUri().equals(audio.getArtUri()) : audio.getArtUri() != null) {
            return false;
        }
        if (getFileUri() != null ? !getFileUri().equals(audio.getFileUri()) : audio.getFileUri() != null) {
            return false;
        }
        if (getPath() != null ? !getPath().equals(audio.getPath()) : audio.getPath() != null) {
            return false;
        }
        if (getMimeType() != null ? !getMimeType().equals(audio.getMimeType()) : audio.getMimeType() != null) {
            return false;
        }
        if (getBitrate() != null ? !getBitrate().equals(audio.getBitrate()) : audio.getBitrate() != null) {
            return false;
        }
        if (getCompilation() != null ? !getCompilation().equals(audio.getCompilation()) : audio.getCompilation() != null) {
            return false;
        }
        if (getComposer() != null ? !getComposer().equals(audio.getComposer()) : audio.getComposer() != null) {
            return false;
        }
        if (getDate() != null ? !getDate().equals(audio.getDate()) : audio.getDate() != null) {
            return false;
        }
        if (getDiscNumber() != null ? !getDiscNumber().equals(audio.getDiscNumber()) : audio.getDiscNumber() != null) {
            return false;
        }
        if (getGenre() != null ? !getGenre().equals(audio.getGenre()) : audio.getGenre() != null) {
            return false;
        }
        if (getHasAudio() != null ? !getHasAudio().equals(audio.getHasAudio()) : audio.getHasAudio() != null) {
            return false;
        }
        if (getHasVideo() != null ? !getHasVideo().equals(audio.getHasVideo()) : audio.getHasVideo() != null) {
            return false;
        }
        if (getNumTracks() != null ? !getNumTracks().equals(audio.getNumTracks()) : audio.getNumTracks() != null) {
            return false;
        }
        if (getTrackNumber() != null ? !getTrackNumber().equals(audio.getTrackNumber()) : audio.getTrackNumber() != null) {
            return false;
        }
        if (getWriter() != null ? !getWriter().equals(audio.getWriter()) : audio.getWriter() != null) {
            return false;
        }
        if (getSamplingRate() != null ? !getSamplingRate().equals(audio.getSamplingRate()) : audio.getSamplingRate() != null) {
            return false;
        }
        return getBitPerSample() != null ? getBitPerSample().equals(audio.getBitPerSample()) : audio.getBitPerSample() == null;
    }
    
    @Override
    public int hashCode() {
        int result = getName() != null ? getName().hashCode() : 0;
        result = 31 * result + (getTitle() != null ? getTitle().hashCode() : 0);
        result = 31 * result + (getArtist() != null ? getArtist().hashCode() : 0);
        result = 31 * result + (getAuthor() != null ? getAuthor().hashCode() : 0);
        result = 31 * result + (getAlbum() != null ? getAlbum().hashCode() : 0);
        result = 31 * result + (getAlbumArtist() != null ? getAlbumArtist().hashCode() : 0);
        result = 31 * result + (getArtUri() != null ? getArtUri().hashCode() : 0);
        result = 31 * result + (getFileUri() != null ? getFileUri().hashCode() : 0);
        result = 31 * result + (getPath() != null ? getPath().hashCode() : 0);
        result = 31 * result + (getMimeType() != null ? getMimeType().hashCode() : 0);
        result = 31 * result + getTrack();
        result = 31 * result + (getYear() != null ? getYear().hashCode() : 0);
        result = 31 * result + getSize();
        result = 31 * result + (getBitrate() != null ? getBitrate().hashCode() : 0);
        result = 31 * result + (getCompilation() != null ? getCompilation().hashCode() : 0);
        result = 31 * result + (getComposer() != null ? getComposer().hashCode() : 0);
        result = 31 * result + (getDate() != null ? getDate().hashCode() : 0);
        result = 31 * result + (getDiscNumber() != null ? getDiscNumber().hashCode() : 0);
        result = 31 * result + (int) (getDuration() ^ (getDuration() >>> 32));
        result = 31 * result + (getGenre() != null ? getGenre().hashCode() : 0);
        result = 31 * result + (getHasAudio() != null ? getHasAudio().hashCode() : 0);
        result = 31 * result + (getHasVideo() != null ? getHasVideo().hashCode() : 0);
        result = 31 * result + (getNumTracks() != null ? getNumTracks().hashCode() : 0);
        result = 31 * result + (getTrackNumber() != null ? getTrackNumber().hashCode() : 0);
        result = 31 * result + (getWriter() != null ? getWriter().hashCode() : 0);
        result = 31 * result + (getSamplingRate() != null ? getSamplingRate().hashCode() : 0);
        result = 31 * result + (getBitPerSample() != null ? getBitPerSample().hashCode() : 0);
        result = 31 * result + (int) (getId() ^ (getId() >>> 32));
        result = 31 * result + (int) (getDateAdded() ^ (getDateAdded() >>> 32));
        result = 31 * result + (int) (getDateModified() ^ (getDateModified() >>> 32));
        result = 31 * result + (int) (getDateTaken() ^ (getDateTaken() >>> 32));
        result = 31 * result + (int) (getAlbumId() ^ (getAlbumId() >>> 32));
        return result;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getAlbumArtist() {
        return albumArtist;
    }
    
    public void setAlbumArtist(String albumArtist) {
        this.albumArtist = albumArtist;
    }
    
    public String getCompilation() {
        return compilation;
    }
    
    public void setCompilation(String compilation) {
        this.compilation = compilation;
    }
    
    public String getComposer() {
        return composer;
    }
    
    public void setComposer(String composer) {
        this.composer = composer;
    }
    
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
    
    public String getDiscNumber() {
        return discNumber;
    }
    
    public void setDiscNumber(String discNumber) {
        this.discNumber = discNumber;
    }
    
    public String getGenre() {
        return genre;
    }
    
    public void setGenre(String genre) {
        this.genre = genre;
    }
    
    public String getHasAudio() {
        return hasAudio;
    }
    
    public void setHasAudio(String hasAudio) {
        this.hasAudio = hasAudio;
    }
    
    public String getHasVideo() {
        return hasVideo;
    }
    
    public void setHasVideo(String hasVideo) {
        this.hasVideo = hasVideo;
    }
    
    public String getNumTracks() {
        return numTracks;
    }
    
    public void setNumTracks(String numTracks) {
        this.numTracks = numTracks;
    }
    
    public String getTrackNumber() {
        return trackNumber;
    }
    
    public void setTrackNumber(String trackNumber) {
        this.trackNumber = trackNumber;
    }
    
    public String getWriter() {
        return writer;
    }
    
    public void setWriter(String writer) {
        this.writer = writer;
    }
    
    public String getSamplingRate() {
        return samplingRate;
    }
    
    public void setSamplingRate(String samplingRate) {
        this.samplingRate = samplingRate;
    }
    
    public String getBitPerSample() {
        return bitPerSample;
    }
    
    public void setBitPerSample(String bitPerSample) {
        this.bitPerSample = bitPerSample;
    }
}
