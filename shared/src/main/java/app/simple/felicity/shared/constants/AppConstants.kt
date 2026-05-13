package app.simple.felicity.shared.constants

object AppConstants {
    /**
     * MusicBrainz requires every API client to identify itself with a User-Agent
     * that includes the application name, version, and a contact URL or email.
     * Requests without a proper User-Agent are rate-limited very aggressively.
     *
     * See: https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting
     */
    const val MUSIC_BRAINZ_USER_AGENT = "Felicity Music Player/1.0 (hamzarizwan243@gmail.com)"
}