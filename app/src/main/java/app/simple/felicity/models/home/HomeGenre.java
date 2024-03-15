package app.simple.felicity.models.home;

import java.util.ArrayList;

import app.simple.felicity.models.Genre;

public class HomeGenre extends Home {
    
    private ArrayList <Genre> genres;
    
    public HomeGenre(int title, int icon, ArrayList <Genre> genres) {
        super(title, icon);
        this.genres = genres;
    }
    
    public ArrayList <Genre> getGenres() {
        return genres;
    }
    
    public void setGenres(ArrayList <Genre> genres) {
        this.genres = genres;
    }
}
