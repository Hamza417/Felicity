package app.simple.felicity.engine.manager;

import java.util.ArrayList;

import app.simple.felicity.repository.models.normal.Audio;

public class AudioDataManager {
    private static final AudioStateManager audioStateManager = AudioStateManager.getInstance();
    private static AudioDataManager instance;
    private static ArrayList <Audio> audioData;
    
    private AudioDataManager() {
    }
    
    public static AudioDataManager getInstance() {
        if (instance == null) {
            instance = new AudioDataManager();
        }
        
        return instance;
    }
    
    public void setAudioDataAndPlay(ArrayList <Audio> audioData, int position) {
        AudioDataManager.audioData = audioData;
        audioStateManager.notifyAudioPlay(audioData, position);
    }
    
    public ArrayList <Audio> getAudioData() {
        return audioData;
    }
    
    public void setAudioData(ArrayList <Audio> audioData) {
        AudioDataManager.audioData = audioData;
    }
    
    public Audio getAudio(int position) {
        return audioData.get(position);
    }
    
    public int getAudioDataSize() {
        return audioData.size();
    }
    
    public void clearAudioData() {
        audioData.clear();
    }
    
    public void addAudioData(Audio audio) {
        audioData.add(audio);
    }
    
    public void removeAudioData(int position) {
        audioData.remove(position);
    }
    
    public void updateAudioData(int position, Audio audio) {
        audioData.set(position, audio);
    }
    
    public void updateAudioData(Audio audio) {
        for (int i = 0; i < audioData.size(); i++) {
            if (audioData.get(i).getId() == audio.getId()) {
                audioData.set(i, audio);
                break;
            }
        }
    }
    
    public void updateAudioData(ArrayList <Audio> audioData) {
        AudioDataManager.audioData = audioData;
    }
}
