package app.simple.felicity.repository.interfaces;

import java.util.ArrayList;

import app.simple.felicity.repository.models.normal.Audio;

public interface AudioStateCallbacks {
    default void onAudioPlay(ArrayList <Audio> audio, int position) {
    }
    
    default void onAudioPause(ArrayList <Audio> audio) {
    }
}
