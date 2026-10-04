package at.fhj.togethergo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Central place for the light/dark mode setting. The choice is stored in SharedPreferences
 * and applied on app start.
 */
public final class ThemeManager {
    private static final String PREFS = "settings";
    private static final String KEY_MODE = "theme_mode";

    private ThemeManager() {
    }

    /**
     * Returns the saved night mode (one of {@code AppCompatDelegate.MODE_NIGHT_*}).
     * Defaults to following the system.
     */
    public static int getMode(Context context) {
        return prefs(context).getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /** Saves the night mode and applies it immediately. */
    public static void setMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** Applies the saved night mode. Called on app start. */
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context));
    }

    /** Returns whether dark mode is currently active, based on the current configuration. */
    public static boolean isDark(Context context) {
        int flags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
