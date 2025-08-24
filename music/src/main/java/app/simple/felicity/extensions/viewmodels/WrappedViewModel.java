package app.simple.felicity.extensions.viewmodels;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import app.simple.felicity.extensions.livedata.ErrorLiveData;
import app.simple.felicity.preferences.ConfigurationPreferences;
import app.simple.felicity.repository.database.instances.StackTraceDatabase;
import app.simple.felicity.utils.ContextUtils;

public class WrappedViewModel extends AndroidViewModel implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    public final ErrorLiveData error = new ErrorLiveData();
    public final MutableLiveData <String> warning = new MutableLiveData <>();
    public final MutableLiveData <Integer> notFound = new MutableLiveData <>();
    
    public WrappedViewModel(@NonNull Application application) {
        super(application);
        app.simple.felicity.manager.SharedPreferences.INSTANCE.registerListener(this);
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
    
    protected void postWarning(String string) {
        warning.postValue(string);
    }
    
    protected void postError(Throwable throwable) {
        error.postError(throwable, getApplication());
    }
    
    public void cleanErrorStack() {
        error.postValue(null);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        try {
            Objects.requireNonNull(StackTraceDatabase.Companion.getInstance()).close();
        } catch (NullPointerException ignored) {
        }
        
        app.simple.felicity.manager.SharedPreferences.INSTANCE.unregisterListener(this);
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
    
    }
}
