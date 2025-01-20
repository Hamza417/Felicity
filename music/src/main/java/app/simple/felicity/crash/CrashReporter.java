package app.simple.felicity.crash;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Objects;

import androidx.annotation.NonNull;
import app.simple.felicity.activities.CrashReporterActivity;
import app.simple.felicity.core.utils.StackTraceUtils;
import app.simple.felicity.preferences.CrashPreferences;
import app.simple.felicity.repository.database.instances.StackTraceDatabase;
import app.simple.felicity.repository.models.normal.StackTrace;

/*
 * Ref: https://stackoverflow.com/questions/601503/how-do-i-obtain-crash-data-from-my-android-application
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {

    private final String TAG = getClass().getSimpleName();
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    public CrashReporter(Context context) {
        this.context = context;
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public void uncaughtException(@NonNull Thread thread, Throwable throwable) {
        final long crashTimeStamp = System.currentTimeMillis();
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);

        throwable.printStackTrace(printWriter);
        String stacktrace = result.toString();
        printWriter.close();

        StackTraceUtils.create(stacktrace, new File(context.getExternalFilesDir("logs"), "crashLog_" + crashTimeStamp));
        CrashPreferences.INSTANCE.saveCrashLog(crashTimeStamp);
        CrashPreferences.INSTANCE.saveMessage(throwable.toString());
        CrashPreferences.INSTANCE.saveCause(StackTraceUtils.getCause(throwable).toString());

        Intent intent = new Intent(context, CrashReporterActivity.class);
        intent.putExtra(CrashReporterActivity.MODE_NORMAL, stacktrace);
        context.startActivity(intent);

        defaultUncaughtExceptionHandler.uncaughtException(thread, throwable);
    }

    public void initialize() {
        long timeStamp = CrashPreferences.INSTANCE.getCrashLog();

        if (timeStamp != CrashPreferences.crashTimestampEmptyDefault) {
            String stack = StackTraceUtils.read(new File(context.getExternalFilesDir("logs"), "crashLog_" + timeStamp));
            Intent intent = new Intent(context, CrashReporterActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(CrashReporterActivity.MODE_NORMAL, stack);
            context.startActivity(intent);
        }

        Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(context));
    }

    @SuppressWarnings("unused")
    public void saveTraceToDataBase(Throwable throwable) {
        new Thread(() -> {
            Log.d(TAG, "Thread started");
            StackTrace stackTrace = new StackTrace(throwable);
            StackTraceDatabase stackTraceDatabase = StackTraceDatabase.Companion.getInstance(context);
            assert stackTraceDatabase != null;
            Objects.requireNonNull(stackTraceDatabase.stackTraceDao()).insertTrace(stackTrace);
            Log.d(TAG, "Trace saved to database");
        }).start();
    }
}
