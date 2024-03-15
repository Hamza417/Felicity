package app.simple.felicity.models.home;

public class HomeModelUtils {
    public static int getHomeDataSize(Home home) {
        if (home instanceof HomeAudio) {
            return ((HomeAudio) home).getAudios().size();
        } else if (home instanceof HomeAlbum) {
            return ((HomeAlbum) home).getAlbums().size();
        } else if (home instanceof HomeGenre) {
            return ((HomeGenre) home).getGenres().size();
        } else if (home instanceof HomeArtist) {
            return ((HomeArtist) home).getArtists().size();
        }
        
        return 0;
    }
}
