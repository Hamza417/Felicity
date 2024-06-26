package app.simple.felicity.models.home;

import java.util.ArrayList;

import app.simple.felicity.models.normal.Album;

public class HomeAlbum extends Home {
    
    private ArrayList <Album> albums;
    
    public HomeAlbum(int title, int icon, ArrayList <Album> albums) {
        super(title, icon, albums.size());
        this.albums = albums;
    }
    
    public ArrayList <Album> getAlbums() {
        return albums;
    }
    
    public void setAlbums(ArrayList <Album> albums) {
        this.albums = albums;
        setSize(albums.size());
    }
}
