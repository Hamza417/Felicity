package app.simple.felicity.repository.listeners;

import app.simple.felicity.repository.models.Audio;

public interface MediaStateListener {
    void onAudioChange(Audio audio);
}
