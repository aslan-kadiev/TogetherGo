package at.fhj.togethergo;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;

/**
 * Application class. Applies one-time settings, such as the saved light/dark theme,
 * before any activity starts.
 */
public class TogetherGoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        enableStrictModeForDebug();
        // restore the saved light/dark choice before any activity is shown
        ThemeManager.apply(this);
    }

    /**
     * Logs accidental disk or network access on the main thread in debug builds.
     * Does nothing in release builds.
     */
    private void enableStrictModeForDebug() {
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debuggable) {
            return;
        }
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build());
    }
}
