package com.hiveworkshop.war3assetsimporter.gui.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads, saves and applies the persisted Look & Feel preference.
 *
 * <p>Configuration is stored as JSON at {@code ~/.war3importer/appearance.json}.
 * If the file is absent, the JVM default L&F is used unchanged.
 * Call {@link #save()} to persist the current choice.
 */
public class AppearanceConfig {

    private static final Path CONFIG_DIR =
            Path.of(System.getProperty("user.home"), ".war3importer");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("appearance.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String KEY_LNF = "lookAndFeel";
    private static final String KEY_LOCALE = "locale";

    /**
     * Fully-qualified class name of the saved L&F, or {@code null} for JVM default.
     */
    private String lookAndFeelClassName = null;

    /**
     * BCP-47 language tag of the saved locale (e.g. "en", "fr"), or {@code null} for JVM default.
     */
    private String localeLanguage = null;

    // -------------------------------------------------------------------------
    // Load / save
    // -------------------------------------------------------------------------

    /**
     * Loads the saved L&F class name from disk.
     * Safe to call on startup — silently ignores read errors.
     */
    public void load() {
        if (!CONFIG_FILE.toFile().exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj != null && obj.has(KEY_LNF)) {
                lookAndFeelClassName = obj.get(KEY_LNF).getAsString();
            }
            if (obj != null && obj.has(KEY_LOCALE)) {
                localeLanguage = obj.get(KEY_LOCALE).getAsString();
            }
        } catch (IOException e) {
            System.err.println("Warning: could not load appearance.json — using default L&F.");
        }
    }

    /**
     * Persists the current L&F class name to {@code ~/.war3importer/appearance.json}.
     */
    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject obj = new JsonObject();
            if (lookAndFeelClassName != null) {
                obj.addProperty(KEY_LNF, lookAndFeelClassName);
            }
            if (localeLanguage != null) {
                obj.addProperty(KEY_LOCALE, localeLanguage);
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(obj, writer);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not save appearance.json: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getLookAndFeelClassName() {
        return lookAndFeelClassName;
    }

    public void setLookAndFeelClassName(String name) {
        lookAndFeelClassName = name;
    }

    public String getLocaleLanguage() {
        return localeLanguage;
    }

    public void setLocaleLanguage(String lang) {
        localeLanguage = lang;
    }

    /**
     * Applies the saved L&F via {@link UIManager#setLookAndFeel}.
     * Does nothing if no L&F has been saved.  Call this <em>before</em> creating
     * any Swing components for best results.
     */
    public void applyIfSet() {
        if (lookAndFeelClassName == null) return;
        try {
            UIManager.setLookAndFeel(lookAndFeelClassName);
        } catch (Exception e) {
            System.err.println("Warning: could not apply saved L&F \"" + lookAndFeelClassName
                    + "\": " + e.getMessage());
        }
    }
}
