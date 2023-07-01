package app.simple.felicity.extensions.viewmodels;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import app.simple.felicity.database.instances.StackTraceDatabase;
import app.simple.felicity.extensions.livedata.ErrorLiveData;
import app.simple.felicity.preferences.ConfigurationPreferences;
import app.simple.felicity.utils.ContextUtils;

public class WrappedViewModel extends AndroidViewModel implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    @SuppressLint ("InlinedApi")
    // Mostly, false positives
    public final String[] audioProjection = {MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_TAKEN,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.COMPOSER,
    };
    
    public final Uri internalContentUri = MediaStore.Audio.Media.INTERNAL_CONTENT_URI;
    public final Uri externalContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    
    public final ErrorLiveData error = new ErrorLiveData();
    public final MutableLiveData <String> warning = new MutableLiveData <>();
    public final MutableLiveData <Integer> notFound = new MutableLiveData <>();
    
    public WrappedViewModel(@NonNull Application application) {
        super(application);
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.registerListener(this);
    }
    
    public final Context getContext() {
        return ContextUtils.Companion.updateLocale(applicationContext(), ConfigurationPreferences.INSTANCE.getAppLanguage());
    }
    
    public LiveData <Throwable> getError() {
        return error;
    }
    
    public LiveData <String> getWarning() {
        return warning;
    }
    
    public LiveData <Integer> getNotFound() {
        return notFound;
    }
    
    public final Context applicationContext() {
        return getApplication().getApplicationContext();
    }
    
    public final String getString(int id) {
        return getContext().getString(id);
    }
    
    public final String getString(int resId, Object... formatArgs) {
        return getContext().getString(resId, formatArgs);
    }
    
    public final ContentResolver getContentResolver() {
        return getApplication().getContentResolver();
    }
    
    public final PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }
    
    protected void postWarning(String string) {
        warning.postValue(string);
    }
    
    protected void postError(Throwable throwable) {
        error.postError(throwable, getApplication());
    }
    
    public void onAppUninstalled(String packageName) {
    
    }
    
    public void cleanErrorStack() {
        error.postValue(null);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        try {
            Objects.requireNonNull(StackTraceDatabase.Companion.getInstance()).close();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.unregisterListener(this);
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
    
    }
}