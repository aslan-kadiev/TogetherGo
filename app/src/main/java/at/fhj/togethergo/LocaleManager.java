package at.fhj.togethergo;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * Wraps AppCompat's per-app language API. The selection is persisted by
 * {@code AppLocalesMetadataHolderService} (declared in the manifest).
 */
public final class LocaleManager {
    private LocaleManager() {
    }

    /**
     * Sets the app language; AppCompat recreates the current activity.
     * @param languageTag language tag, e.g. "de" or "en"
     */
    public static void setLanguage(String languageTag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
    }

    /** Returns the current app language tag, e.g. "en" or "de". */
    public static String getCurrentLanguage() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty() && locales.get(0) != null) {
            return locales.get(0).getLanguage();
        }
        return Locale.getDefault().getLanguage();
    }
}
