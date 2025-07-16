package app.simple.felicity.engine.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import app.simple.felicity.repository.interfaces.AudioStateCallbacks;
import app.simple.felicity.repository.models.normal.Audio;

public class AudioStateManager {
    private static AudioStateManager instance;
    private final Set <AudioStateCallbacks> audioStateCallbacks = new HashSet <>();
    
    private AudioStateManager() {
    }
    
    public static AudioStateManager getInstance() {
        if (instance == null) {
            instance = new AudioStateManager();
        }
        return instance;
    }
    
    public void registerAudioStateCallbacks(AudioStateCallbacks audioStateCallbacks) {
        this.audioStateCallbacks.add(audioStateCallbacks);
    }
    
    public void unregisterAudioStateCallbacks(AudioStateCallbacks audioStateCallbacks) {
        this.audioStateCallbacks.remove(audioStateCallbacks);
    }
    
    public void notifyAudioPlay(ArrayList <Audio> audio, int position) {
        for (AudioStateCallbacks audioStateCallback : audioStateCallbacks) {
            audioStateCallback.onAudioPlay(audio, position);
        }
    }
    
    public void notifyAudioPause(ArrayList <Audio> audio) {
        for (AudioStateCallbacks audioStateCallback : audioStateCallbacks) {
            audioStateCallback.onAudioPause(audio);
        }
    }
}
