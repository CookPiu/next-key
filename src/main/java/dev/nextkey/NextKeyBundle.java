package dev.nextkey;

import com.intellij.DynamicBundle;
import com.intellij.openapi.diagnostic.Logger;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Interface strings. Follows the IDE language (Settings | Appearance &amp; Behavior |
 * System Settings | Language and Region) rather than the operating system locale — plenty of
 * people run an English IDE on a non-English system. English by default; Simplified Chinese
 * comes from {@code NextKeyBundle_zh_CN.properties} when that language is selected.
 */
final class NextKeyBundle {

    private static final String NAME = "messages.NextKeyBundle";
    private static final Logger LOG = Logger.getInstance(NextKeyBundle.class);

    private static volatile ResourceBundle bundle;
    private static volatile boolean loaded;

    private NextKeyBundle() {
    }

    static String message(String key, Object... params) {
        String pattern = key;
        ResourceBundle resources = bundle();
        if (resources != null) {
            try {
                pattern = resources.getString(key);
            } catch (MissingResourceException ignored) {
                // Fall back to the key itself: an odd label beats a broken dialog
            }
        }
        return params.length == 0 ? pattern : MessageFormat.format(pattern, params);
    }

    /** Loaded lazily: this class can be touched before the IDE's localization is ready. */
    private static ResourceBundle bundle() {
        if (!loaded) {
            synchronized (NextKeyBundle.class) {
                if (!loaded) {
                    bundle = load();
                    loaded = true;
                }
            }
        }
        return bundle;
    }

    private static ResourceBundle load() {
        ResourceBundle result = doLoad();
        // Picking the wrong source for the language only shows up on particular machines,
        // so leave a line behind to make it checkable after the fact
        LOG.info("[next-key] IDE locale=" + ideLocale()
                + " loaded=" + (result == null ? "none" : "'" + result.getLocale() + "'"));
        return result;
    }

    private static ResourceBundle doLoad() {
        ClassLoader loader = NextKeyBundle.class.getClassLoader();
        // getBundle searches "requested locale, then JVM default locale, then the base bundle".
        // That middle step would hand Chinese strings to an English IDE running on a Chinese
        // system, so it has to go.
        ResourceBundle.Control control =
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        try {
            return ResourceBundle.getBundle(NAME, ideLocale(), loader, control);
        } catch (Throwable ignored) {
            // No file for that language; fall through to English
        }
        try {
            return ResourceBundle.getBundle(NAME, Locale.ROOT, loader, control);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Locale ideLocale() {
        try {
            Locale locale = DynamicBundle.getLocale();
            if (locale != null) {
                return locale;
            }
        } catch (Throwable ignored) {
            // Without that API fall back to English rather than to the system locale
        }
        return Locale.ENGLISH;
    }
}
