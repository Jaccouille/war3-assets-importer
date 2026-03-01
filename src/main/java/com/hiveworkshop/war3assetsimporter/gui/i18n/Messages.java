package com.hiveworkshop.war3assetsimporter.gui.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Centralised i18n helper for all GUI string lookups.
 *
 * <p>Usage:
 * <pre>
 *   JButton btn = new JButton(Messages.get("button.openMap"));
 *   Messages.setLocale(Locale.FRENCH);  // switch language at runtime
 * </pre>
 *
 * <p>If a key is missing the value {@code !key!} is returned so broken keys are
 * immediately visible without crashing the application.
 */
public final class Messages {

    private static final ResourceBundle.Control UTF8_CONTROL = new ResourceBundle.Control() {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload) throws IOException {
            String resourceName = toResourceName(toBundleName(baseName, locale), "properties");
            try (InputStream is = loader.getResourceAsStream(resourceName)) {
                if (is == null) return null;
                return new PropertyResourceBundle(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        }
    };

    private static ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.ENGLISH, UTF8_CONTROL);

    private Messages() {
    }

    /**
     * Returns the current locale used for string lookups.
     */
    public static Locale getLocale() {
        return bundle.getLocale();
    }

    /**
     * Switches the active locale and reloads the resource bundle.
     * Call {@code applyI18n()} on all GUI components afterwards to refresh labels.
     */
    public static void setLocale(Locale locale) {
        bundle = ResourceBundle.getBundle("messages", locale, UTF8_CONTROL);
        Locale.setDefault(locale);
    }

    /**
     * Returns the translated string for {@code key}, or {@code !key!} if not found.
     */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }
}
