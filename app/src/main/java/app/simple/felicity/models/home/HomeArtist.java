package app.simple.felicity.models.home;

import java.util.ArrayList;

import app.simple.felicity.models.normal.Artist;

public class HomeArtist extends Home {
    
    private ArrayList <Artist> artists;
    
    public HomeArtist(int title, int icon, ArrayList <Artist> artists) {
        super(title, icon, artists.size());
        this.artists = artists;
    }
    
    public ArrayList <Artist> getArtists() {
        return artists;
    }
    
    public void setArtists(ArrayList <Artist> artists) {
        this.artists = artists;
        setSize(artists.size());
    }
}
