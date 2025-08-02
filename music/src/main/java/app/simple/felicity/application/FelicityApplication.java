package app.simple.felicity.application;

import android.app.Application;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class FelicityApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize any libraries or components here if needed
    }
}
