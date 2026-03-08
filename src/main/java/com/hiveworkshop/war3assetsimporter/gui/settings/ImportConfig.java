package com.hiveworkshop.war3assetsimporter.gui.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the Import Configuration panel state to
 * {@code ~/.war3importer/import-config.json}.
 *
 * <p>Load once at startup with {@link #load()}, apply to the panel, then
 * call {@link #save()} on application exit.
 */
public class ImportConfig {

    private static final Path CONFIG_DIR =
            Path.of(System.getProperty("user.home"), ".war3importer");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("import-config.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---- Unit Creation ----
    private boolean createUnits = true;
    private boolean placeUnits = true;
    private boolean clearUnits = false;
    private boolean clearAssets = false;
    private boolean createAlternateUnits = true;
    private boolean flattenPaths = false;
    private double unitScaling = 1.0;

    // ---- Unit Placement ----
    private String unitOriginId = "hfoo";
    private double unitAngle = 270.0;
    private double spacingX = 16.0;
    private double spacingY = 16.0;
    private String placingOrder = "ROWS";

    // ---- Unit Naming ----
    private boolean autoNameUnits = false;
    private boolean autoAssignIcon = false;
    private String nameFormat = "Space Separated (keep case)";

    // -------------------------------------------------------------------------
    // Load / save
    // -------------------------------------------------------------------------

    public void load() {
        if (!CONFIG_FILE.toFile().exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return;
            if (obj.has("createUnits"))          createUnits          = obj.get("createUnits").getAsBoolean();
            if (obj.has("placeUnits"))           placeUnits           = obj.get("placeUnits").getAsBoolean();
            if (obj.has("clearUnits"))           clearUnits           = obj.get("clearUnits").getAsBoolean();
            if (obj.has("clearAssets"))          clearAssets          = obj.get("clearAssets").getAsBoolean();
            if (obj.has("createAlternateUnits")) createAlternateUnits = obj.get("createAlternateUnits").getAsBoolean();
            if (obj.has("flattenPaths"))         flattenPaths         = obj.get("flattenPaths").getAsBoolean();
            if (obj.has("unitScaling"))          unitScaling          = obj.get("unitScaling").getAsDouble();
            if (obj.has("unitOriginId"))         unitOriginId         = obj.get("unitOriginId").getAsString();
            if (obj.has("unitAngle"))            unitAngle            = obj.get("unitAngle").getAsDouble();
            if (obj.has("spacingX"))             spacingX             = obj.get("spacingX").getAsDouble();
            if (obj.has("spacingY"))             spacingY             = obj.get("spacingY").getAsDouble();
            if (obj.has("placingOrder"))         placingOrder         = obj.get("placingOrder").getAsString();
            if (obj.has("autoNameUnits"))        autoNameUnits        = obj.get("autoNameUnits").getAsBoolean();
            if (obj.has("autoAssignIcon"))       autoAssignIcon       = obj.get("autoAssignIcon").getAsBoolean();
            if (obj.has("nameFormat"))           nameFormat           = obj.get("nameFormat").getAsString();
        } catch (IOException e) {
            System.err.println("Warning: could not load import-config.json — using defaults.");
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject obj = new JsonObject();
            obj.addProperty("createUnits",          createUnits);
            obj.addProperty("placeUnits",           placeUnits);
            obj.addProperty("clearUnits",           clearUnits);
            obj.addProperty("clearAssets",          clearAssets);
            obj.addProperty("createAlternateUnits", createAlternateUnits);
            obj.addProperty("flattenPaths",         flattenPaths);
            obj.addProperty("unitScaling",          unitScaling);
            obj.addProperty("unitOriginId",         unitOriginId);
            obj.addProperty("unitAngle",            unitAngle);
            obj.addProperty("spacingX",             spacingX);
            obj.addProperty("spacingY",             spacingY);
            obj.addProperty("placingOrder",         placingOrder);
            obj.addProperty("autoNameUnits",        autoNameUnits);
            obj.addProperty("autoAssignIcon",       autoAssignIcon);
            obj.addProperty("nameFormat",           nameFormat);
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(obj, writer);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not save import-config.json: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isCreateUnits()          { return createUnits; }
    public void setCreateUnits(boolean v)   { createUnits = v; }

    public boolean isPlaceUnits()           { return placeUnits; }
    public void setPlaceUnits(boolean v)    { placeUnits = v; }

    public boolean isClearUnits()           { return clearUnits; }
    public void setClearUnits(boolean v)    { clearUnits = v; }

    public boolean isClearAssets()          { return clearAssets; }
    public void setClearAssets(boolean v)   { clearAssets = v; }

    public boolean isCreateAlternateUnits()         { return createAlternateUnits; }
    public void setCreateAlternateUnits(boolean v)  { createAlternateUnits = v; }

    public boolean isFlattenPaths()         { return flattenPaths; }
    public void setFlattenPaths(boolean v)  { flattenPaths = v; }

    public double getUnitScaling()          { return unitScaling; }
    public void setUnitScaling(double v)    { unitScaling = v; }

    public String getUnitOriginId()         { return unitOriginId; }
    public void setUnitOriginId(String v)   { unitOriginId = v; }

    public double getUnitAngle()            { return unitAngle; }
    public void setUnitAngle(double v)      { unitAngle = v; }

    public double getSpacingX()             { return spacingX; }
    public void setSpacingX(double v)       { spacingX = v; }

    public double getSpacingY()             { return spacingY; }
    public void setSpacingY(double v)       { spacingY = v; }

    public String getPlacingOrder()         { return placingOrder; }
    public void setPlacingOrder(String v)   { placingOrder = v; }

    public boolean isAutoNameUnits()        { return autoNameUnits; }
    public void setAutoNameUnits(boolean v) { autoNameUnits = v; }

    public boolean isAutoAssignIcon()       { return autoAssignIcon; }
    public void setAutoAssignIcon(boolean v){ autoAssignIcon = v; }

    public String getNameFormat()           { return nameFormat; }
    public void setNameFormat(String v)     { nameFormat = v; }
}
