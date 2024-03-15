package app.simple.felicity.models.home;

import android.net.Uri;
import android.widget.ImageView;

import app.simple.felicity.glide.albumcover.AlbumCoverUtils;
import app.simple.felicity.glide.utils.AudioCoverUtil;

public class HomeUtils {
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
    
    public static void loadHomeAlbumArt(Home home, ImageView imageView, int position) {
        if (home instanceof HomeAudio) {
            AudioCoverUtil.INSTANCE.loadFromUri(imageView, Uri.parse(((HomeAudio) home).getAudios().get(position).getArtUri()));
        } else if (home instanceof HomeAlbum) {
            AlbumCoverUtils.INSTANCE.loadAlbumCoverSquare(imageView, ((HomeAlbum) home).getAlbums().get(position).getAlbumId());
        } else if (home instanceof HomeGenre) {
        
        } else if (home instanceof HomeArtist) {
        
        }
    }
}
