package org.example.gui.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads, saves and exposes hotkey configuration for the application.
 *
 * <p>Configuration is persisted as JSON at {@code ~/.war3importer/keybindings.json}.
 * If the file is absent, defaults are used. Call {@link #save()} to persist changes.
 *
 * <p>Keystroke strings follow the format accepted by {@link KeyStroke#getKeyStroke(String)},
 * e.g. {@code "ctrl O"}, {@code "ctrl shift P"}, {@code "ctrl COMMA"}.
 */
public class KeybindingsConfig {

    /** Named actions that can have a keybinding assigned. */
    public static final String ACTION_OPEN_MAP = "openMap";
    public static final String ACTION_IMPORT_MODELS = "importModels";
    public static final String ACTION_PROCESS = "process";
    public static final String ACTION_SETTINGS = "settings";

    /** Default key bindings applied when no config file exists. */
    public static final Map<String, String> DEFAULTS = Map.of(
            ACTION_OPEN_MAP, "ctrl O",
            ACTION_IMPORT_MODELS, "ctrl I",
            ACTION_PROCESS, "ctrl shift P",
            ACTION_SETTINGS, "ctrl COMMA"
    );

    private static final Path CONFIG_DIR =
            Path.of(System.getProperty("user.home"), ".war3importer");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("keybindings.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Mutable map of action → keystroke string. */
    private final Map<String, String> bindings = new LinkedHashMap<>(DEFAULTS);

    // -------------------------------------------------------------------------
    // Load / save
    // -------------------------------------------------------------------------

    /**
     * Loads bindings from disk, falling back to defaults for any missing key.
     * Safe to call on startup — silently ignores read errors.
     */
    public void load() {
        if (!CONFIG_FILE.toFile().exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                // Apply loaded values over defaults (so new defaults are preserved)
                bindings.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not load keybindings.json — using defaults.");
        }
    }

    /**
     * Persists current bindings to {@code ~/.war3importer/keybindings.json}.
     */
    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(bindings, writer);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not save keybindings.json: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the keystroke string for {@code action}, or {@code null} if unbound.
     */
    public String getBinding(String action) {
        return bindings.get(action);
    }

    /**
     * Returns a resolved {@link KeyStroke} for {@code action}, or {@code null} if unbound
     * or the string is not a valid keystroke.
     */
    public KeyStroke getKeyStroke(String action) {
        String ks = bindings.get(action);
        return ks != null ? KeyStroke.getKeyStroke(ks) : null;
    }

    /** Sets a new keystroke string for {@code action}. Does NOT auto-save. */
    public void setBinding(String action, String keystrokeString) {
        bindings.put(action, keystrokeString);
    }

    /** Returns a copy of all current bindings. */
    public Map<String, String> getAllBindings() {
        return new LinkedHashMap<>(bindings);
    }

    /** Resets all bindings to the built-in defaults. Does NOT auto-save. */
    public void resetToDefaults() {
        bindings.clear();
        bindings.putAll(DEFAULTS);
    }

    /** Returns the path to the config file (for display purposes). */
    public static Path getConfigFilePath() {
        return CONFIG_FILE;
    }
}
