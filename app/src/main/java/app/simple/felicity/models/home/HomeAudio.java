package app.simple.felicity.models.home;

import java.util.ArrayList;

import app.simple.felicity.models.Audio;

public class HomeAudio extends Home {
    
    private ArrayList <Audio> audios;
    
    public HomeAudio(int title, int icon, ArrayList <Audio> audios) {
        super(title, icon);
        this.audios = audios;
    }
    
    public ArrayList <Audio> getAudios() {
        return audios;
    }
    
    public void setAudios(ArrayList <Audio> audios) {
        this.audios = audios;
    }
}
