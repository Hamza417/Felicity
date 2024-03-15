package app.simple.felicity.glide.artistcover

object ArtistCoverUtils {
    fun getArtistCoverUrl(artistId: String): String {
        return "http://musicbrainz.org/ws/2/artist/$artistId?inc=url-rels&fmt=json"
    }
}
