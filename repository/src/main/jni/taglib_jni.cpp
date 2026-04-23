/**
 * taglib_jni.cpp
 *
 * Thin JNI bridge between Kotlin and TagLib. This file handles both reading
 * and writing audio metadata. The only currency it deals in is raw POSIX file
 * descriptors — so it works with both regular files and SAF content:// URIs
 * without needing to know anything about the actual path on disk.
 *
 * Why file descriptors instead of file paths? Because when the user picks a
 * folder with the SAF (Storage Access Framework) folder picker, Android hands
 * us a content:// URI — not a real path. We can open that URI as a
 * ParcelFileDescriptor and pass its underlying int fd here. TagLib then reads
 * or writes the file via the fd without ever needing to know the actual path.
 *
 * We dup() the fd before handing it to TagLib so that TagLib can own and
 * eventually close its copy while Java's ParcelFileDescriptor still holds
 * (and safely closes) the original. Without the dup, TagLib closing the fd
 * would corrupt Java's file handle and vice-versa.
 *
 * @author Hamza417
 */

#include <jni.h>
#include <unistd.h>
#include <android/log.h>

#include <taglib/fileref.h>
#include <taglib/tfilestream.h>
#include <taglib/tag.h>
#include <taglib/audioproperties.h>
#include <taglib/tpropertymap.h>
#include <taglib/tvariant.h>

#include <string>
#include "taglib/tstring.h"

#define LOG_TAG "TagLibJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Converts a TagLib string to a Java/Kotlin String, returning nullptr for
// empty strings so Kotlin sees them as null (cleaner than empty strings).
static jstring toJString(JNIEnv *env, const TagLib::String &str) {
    if (str.isEmpty()) return nullptr;
    std::string utf8 = str.to8Bit(true /* utf8 */);
    return env->NewStringUTF(utf8.c_str());
}

// Looks up the first value for a given property-map key, returning nullptr
// if the key is absent or its list is empty.
static jstring getProperty(JNIEnv *env, const TagLib::PropertyMap &props, const char *key) {
    TagLib::String k(key);
    auto it = props.find(k);
    if (it == props.end() || it->second.isEmpty()) return nullptr;
    return toJString(env, it->second.front());
}

// Converts a jstring to a TagLib::String (UTF-8). Returns an empty TagLib
// string if the jstring is null, so callers don't have to guard every call.
static TagLib::String fromJString(JNIEnv *env, jstring js) {
    if (!js) return TagLib::String();
    const char *chars = env->GetStringUTFChars(js, nullptr);
    TagLib::String s(chars, TagLib::String::UTF8);
    env->ReleaseStringUTFChars(js, chars);
    return s;
}

// Sets a property map entry only when the value string isn't null,
// and also clears it out when an empty string is passed so the user
// can intentionally erase a field (null = leave it alone, "" = delete it).
static void applyProperty(JNIEnv *env, TagLib::PropertyMap &props,
                          const char *key, jstring js) {
    if (!js) return; // null means "don't touch this field"
    TagLib::String value = fromJString(env, js);
    if (value.isEmpty()) {
        props.erase(TagLib::String(key)); // empty string = erase the field
    } else {
        TagLib::StringList list;
        list.append(value);
        props[TagLib::String(key)] = list;
    }
}

extern "C" {

/**
 * Opens the audio file by its raw file descriptor and extracts every tag
 * field plus audio properties. Returns a fully populated TagLibMetadata
 * Kotlin object, or null if anything goes wrong (bad fd, unsupported format,
 * missing tags, etc.).
 */
JNIEXPORT jobject JNICALL
Java_app_simple_felicity_repository_metadata_TagLibBridge_nativeLoadFromFd(
        JNIEnv *env, jobject thiz, jint fd) {

    // Duplicate the fd so TagLib can own its copy and close it at the end of
    // the stream's lifetime, while the caller's fd stays valid and closeable.
    int dupFd = dup(static_cast<int>(fd));
    if (dupFd < 0) {
        LOGE("dup() failed for fd=%d — cannot read tags", fd);
        return nullptr;
    }

    // Wrap the duplicated fd in a TagLib stream.
    // true = TagLib closes the fd when the stream is destroyed.
    TagLib::FileStream stream(dupFd, true);

    // Ask TagLib to figure out the format from the file's magic bytes.
    TagLib::FileRef fileRef(
            static_cast<TagLib::IOStream *>(&stream),
            true,
            TagLib::AudioProperties::ReadStyle::Fast
    );
    if (fileRef.isNull()) {
        LOGE("FileRef is null for fd=%d — unsupported format or corrupt file", fd);
        return nullptr;
    }

    // Collect tag fields, guarding every access so a missing tag never crashes.
    TagLib::Tag *tag = fileRef.tag();
    TagLib::AudioProperties *ap = fileRef.audioProperties();
    TagLib::PropertyMap props;
    if (tag) props = tag->properties();

    jstring title = tag ? toJString(env, tag->title()) : nullptr;
    jstring artist = tag ? toJString(env, tag->artist()) : nullptr;
    jstring album = tag ? toJString(env, tag->album()) : nullptr;
    jstring genre = tag ? toJString(env, tag->genre()) : nullptr;
    jstring comment = tag ? toJString(env, tag->comment()) : nullptr;

    // Year comes as an unsigned int — convert to a string so it matches the
    // String field in the Audio model and plays nicely with the rest of the
    // metadata pipeline (some tracks have "2023", others have "2023-04-21").
    jstring year = nullptr;
    if (tag && tag->year() > 0) {
        std::string yearStr = std::to_string(tag->year());
        year = env->NewStringUTF(yearStr.c_str());
    }

    // Richer fields come from the property map which covers ID3v2, Vorbis
    // comments, MP4 atoms, APE tags, etc. in a unified way.
    jstring albumArtist = getProperty(env, props, "ALBUMARTIST");
    if (!albumArtist) albumArtist = getProperty(env, props, "ALBUM ARTIST");
    jstring composer = getProperty(env, props, "COMPOSER");
    jstring lyricist = getProperty(env, props, "LYRICIST");
    jstring discNumber = getProperty(env, props, "DISCNUMBER");
    jstring trackNumber = getProperty(env, props, "TRACKNUMBER");
    jstring numTracks = getProperty(env, props, "TRACKTOTAL");
    if (!numTracks) numTracks = getProperty(env, props, "TOTALTRACKS");
    jstring compilation = getProperty(env, props, "COMPILATION");

    // AudioProperties gives us the technical stuff — duration in milliseconds,
    // bitrate in kbps, sample rate in Hz, and bit depth.
    jlong duration = ap ? static_cast<jlong>(ap->lengthInMilliseconds()) : 0L;
    jlong bitrate = ap ? static_cast<jlong>(ap->bitrate()) : 0L;
    jlong sampleRate = ap ? static_cast<jlong>(ap->sampleRate()) : 0L;

    // Find the TagLibMetadata Kotlin data class and call its constructor.
    jclass metaClass = env->FindClass(
            "app/simple/felicity/repository/metadata/TagLibMetadata");
    if (!metaClass) {
        LOGE("Cannot find TagLibMetadata class — check the package name");
        return nullptr;
    }

    // The constructor signature must match the order and types in TagLibMetadata.kt
    // exactly: 13 nullable Strings followed by 4 Longs.
    jmethodID ctor = env->GetMethodID(metaClass, "<init>",
                                      "("
                                      "Ljava/lang/String;"   // title
                                      "Ljava/lang/String;"   // artist
                                      "Ljava/lang/String;"   // album
                                      "Ljava/lang/String;"   // genre
                                      "Ljava/lang/String;"   // year
                                      "Ljava/lang/String;"   // comment
                                      "Ljava/lang/String;"   // albumArtist
                                      "Ljava/lang/String;"   // composer
                                      "Ljava/lang/String;"   // lyricist
                                      "Ljava/lang/String;"   // discNumber
                                      "Ljava/lang/String;"   // trackNumber
                                      "Ljava/lang/String;"   // numTracks
                                      "Ljava/lang/String;"   // compilation
                                      "JJJJ"                 // duration, bitrate, sampleRate, bitsPerSample
                                      ")V");

    if (!ctor) {
        LOGE("Cannot find TagLibMetadata constructor — signature mismatch");
        return nullptr;
    }

    return env->NewObject(
            metaClass, ctor,
            title, artist, album, genre, year, comment,
            albumArtist, composer, lyricist, discNumber, trackNumber, numTracks, compilation,
            duration, bitrate, sampleRate, 0L);
}

/**
 * Writes tag fields back into an audio file through a writable file descriptor.
 *
 * The fd must have been opened for reading AND writing (O_RDWR / "rw" mode).
 * On the Kotlin side you get that by calling ContentResolver.openFileDescriptor
 * with "rw" for SAF URIs, or ParcelFileDescriptor.open(file, MODE_READ_WRITE)
 * for regular files.
 *
 * Any field passed as null is left exactly as it was in the file.
 * Passing an empty string ("") will erase that field from the tag.
 *
 * Returns JNI_TRUE on success, JNI_FALSE if anything went sideways.
 */
JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_repository_metadata_TagLibBridge_nativeSaveToFd(
        JNIEnv *env, jobject thiz, jint fd,
        jstring title, jstring artist, jstring album, jstring albumArtist,
        jstring year, jstring trackNumber, jstring numTracks, jstring discNumber,
        jstring genre, jstring composer, jstring lyricist, jstring compilation,
        jstring comment, jstring lyrics) {

    // Duplicate the fd so TagLib can own and eventually close its copy while
    // the Java ParcelFileDescriptor still holds the original safely.
    int dupFd = dup(static_cast<int>(fd));
    if (dupFd < 0) {
        LOGE("dup() failed for fd=%d — cannot write tags", fd);
        return JNI_FALSE;
    }

    // true = TagLib closes the dup'd fd when the stream is destroyed.
    TagLib::FileStream stream(dupFd, false);

    // readAudioProperties=false here — we just want to write tags,
    // no need to decode audio to gather properties (saves time).
    TagLib::FileRef fileRef(&stream, false);
    if (fileRef.isNull()) {
        LOGE("FileRef is null for fd=%d — unsupported format or corrupt file", fd);
        return JNI_FALSE;
    }

    TagLib::Tag *tag = fileRef.tag();
    if (!tag) {
        LOGE("File has no writable tag for fd=%d", fd);
        return JNI_FALSE;
    }

    // The simple tag fields (title, artist, album, genre, comment) get written
    // via the basic Tag API so they end up in the right frame/atom for each format.
    if (title) tag->setTitle(fromJString(env, title));
    if (artist) tag->setArtist(fromJString(env, artist));
    if (album) tag->setAlbum(fromJString(env, album));
    if (genre) tag->setGenre(fromJString(env, genre));
    if (comment) tag->setComment(fromJString(env, comment));

    // Year is stored as a uint in the basic API — convert the string if it looks
    // like a plain four-digit year; otherwise store it via the property map below.
    if (year) {
        TagLib::String yearStr = fromJString(env, year);
        unsigned int yearUint = static_cast<unsigned int>(std::strtoul(yearStr.toCString(), nullptr,
                                                                       10));
        if (yearUint > 0) tag->setYear(yearUint);
    }

    // Richer / format-specific fields go through the unified property map, which
    // TagLib translates to the right ID3v2 frame, Vorbis comment, MP4 atom, etc.
    TagLib::PropertyMap props = tag->properties();

    applyProperty(env, props, "ALBUMARTIST", albumArtist);
    applyProperty(env, props, "COMPOSER", composer);
    applyProperty(env, props, "LYRICIST", lyricist);
    applyProperty(env, props, "DISCNUMBER", discNumber);
    applyProperty(env, props, "TRACKNUMBER", trackNumber);
    applyProperty(env, props, "TRACKTOTAL", numTracks);
    applyProperty(env, props, "COMPILATION", compilation);
    applyProperty(env, props, "LYRICS", lyrics);

    tag->setProperties(props);

    if (!fileRef.save()) {
        LOGE("TagLib save() failed for fd=%d", fd);
        return JNI_FALSE;
    }

    LOGI("Tags saved successfully for fd=%d", fd);
    return JNI_TRUE;
}

/**
 * Extracts the embedded lyrics text (plain or synced LRC) from an audio file
 * using TagLib's unified property map. This covers USLT frames in MP3s, the
 * ©lyr atom in M4A files, LYRICS Vorbis comments in FLAC/OGG, and every other
 * format TagLib knows about — all from one call, no extra libraries needed.
 *
 * We check "LYRICS" first (the most common key), then fall back to
 * "UNSYNCEDLYRICS" which some taggers use for plain-text lyrics.
 *
 * @param fd A readable POSIX file descriptor pointing at an audio file.
 * @return The embedded lyrics string, or null if none were found.
 */
JNIEXPORT jstring JNICALL
Java_app_simple_felicity_repository_metadata_TagLibBridge_nativeExtractLyricsFromFd(
        JNIEnv *env, jobject thiz, jint fd) {

    int dupFd = dup(static_cast<int>(fd));
    if (dupFd < 0) {
        LOGE("dup() failed for fd=%d — cannot extract lyrics", fd);
        return nullptr;
    }

    // We only need to read here, so skip the audio properties scan for speed.
    TagLib::FileStream stream(dupFd, true);
    TagLib::FileRef fileRef(&stream, false);
    if (fileRef.isNull()) {
        LOGE("FileRef is null for fd=%d — unsupported format", fd);
        return nullptr;
    }

    TagLib::Tag *tag = fileRef.tag();
    if (!tag) return nullptr;

    TagLib::PropertyMap props = tag->properties();

    // "LYRICS" is the standard key across ID3v2 (USLT), M4A (©lyr), and Vorbis.
    jstring lyrics = getProperty(env, props, "LYRICS");
    if (lyrics) return lyrics;

    // Some taggers (e.g., older foobar2000 versions) write plain lyrics under
    // "UNSYNCEDLYRICS" — worth a quick look before giving up.
    return getProperty(env, props, "UNSYNCEDLYRICS");
}

} // extern "C"

