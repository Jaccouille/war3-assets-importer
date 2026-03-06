package com.hiveworkshop.war3assetsimporter.core.service;

import com.hiveworkshop.war3assetsimporter.core.model.ExistingUnit;
import com.hiveworkshop.war3assetsimporter.core.model.MapMetadata;
import com.hiveworkshop.war3assetsimporter.core.model.UnitEntry;
import com.hiveworkshop.war3assetsimporter.core.util.CameraBounds;
import com.hiveworkshop.war3assetsimporter.core.util.StringUtils;
import net.moonlightflower.wc3libs.bin.Wc3BinInputStream;
import net.moonlightflower.wc3libs.bin.app.DOO_UNITS;
import net.moonlightflower.wc3libs.bin.app.W3I;
import net.moonlightflower.wc3libs.bin.app.objMod.W3U;
import net.moonlightflower.wc3libs.dataTypes.app.Coords3DF;
import net.moonlightflower.wc3libs.misc.MetaFieldId;
import net.moonlightflower.wc3libs.txt.WTS;
import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads Warcraft 3 map metadata (name, author, version, description, preview image,
 * camera bounds) from a .w3x/.w3m MPQ archive.
 * Used by both the GUI (to populate MapOptionsPanel) and the CLI (for informational output).
 */
public class MapMetadataService {

    private static final Logger LOG = Logger.getLogger(MapMetadataService.class.getName());
    private static final int DOO_UNITS_HEADER_SIZE = 16;
    private static final int MIN_DOO_UNITS_SUBVERSION = 11;

    /**
     * Resolves a value from a W3I field that may be either:
     * <ul>
     *   <li>A plain string (non-localised maps) — returned as-is.</li>
     *   <li>A WTS trigger-string reference like {@code "TRIGSTR_001"} — looked up
     *       in {@code namedEntries}.  Several key formats are tried in order
     *       ({@code "TRIGSTR_001"}, {@code "001"}, {@code "STRING 1"}) to cope with
     *       different wc3libs serialisation styles.</li>
     * </ul>
     *
     * @param raw          the raw string stored in the W3I field (may be {@code null})
     * @param namedEntries the WTS entry map returned by {@link WTS#getNamedEntries()}
     * @param fallback     the value to return when {@code raw} is {@code null} or blank
     * @return the resolved, trimmed display string
     */
    private static String resolveTrigStr(String raw, Map<String, String> namedEntries, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;

        // 1. Direct lookup — works when wc3libs uses "TRIGSTR_NNN" as map key
        String resolved = namedEntries.get(raw);
        if (resolved != null) return resolved.trim();

        // 2. The raw value is a TRIGSTR reference; WTS may use a different key format
        if (raw.startsWith("TRIGSTR_")) {
            String numPart = raw.substring("TRIGSTR_".length()); // e.g. "001"

            // 2a. Bare numeric string: "001"
            resolved = namedEntries.get(numPart);
            if (resolved != null) return resolved.trim();

            // 2b. "STRING N" with decimal (no leading zeros): "STRING 1"
            try {
                int num = Integer.parseInt(numPart);
                resolved = namedEntries.get("STRING " + num);
                if (resolved != null) return resolved.trim();
            } catch (NumberFormatException ignored) {
            }

            // 2c. Fallback: the TRIGSTR token itself is the best we can show
            return raw;
        }

        // 3. Not a TRIGSTR reference — it is the literal string value
        return raw.trim();
    }

    private static int readUInt32LE(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    /**
     * Opens the map MPQ, extracts W3I + WTS files, and returns a {@link MapMetadata} record.
     *
     * <p>The preview image is always extracted when present.  If the W3I cannot be parsed
     * (e.g. a Reforged map with an unsupported format version), the method still returns a
     * valid {@link MapMetadata} whose {@link MapMetadata#loadWarning()} is non-null rather
     * than propagating the exception, so the UI can display the preview and allow the user
     * to proceed with the import.
     *
     * @param mapFile the .w3x or .w3m map file
     * @return parsed (or partially parsed) map metadata — never {@code null}
     * @throws Exception only if the MPQ archive itself cannot be opened
     */
    public MapMetadata loadMetadata(File mapFile) throws Exception {
        LOG.info("Loading map metadata: " + mapFile.getAbsolutePath());
        try (JMpqEditor mpqEditor = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {

            // --- Step 1: preview image (independent of W3I format) ---
            byte[] previewBytes = null;
            if (mpqEditor.hasFile("war3mapMap.blp")) {
                previewBytes = mpqEditor.extractFileAsBytes("war3mapMap.blp");
            }

            // --- Step 2: W3I (may fail for Reforged / unknown format versions) ---
            W3I w3i;
            try {
                w3i = new W3I(mpqEditor.extractFileAsBytes("war3map.w3i"));
            } catch (Exception e) {
                // Return partial metadata so the UI still shows the preview and
                // the map file remains selectable for processing.
                return new MapMetadata("", "", "", "", "",
                        previewBytes, CameraBounds.getInstance(),
                        "W3I metadata not available (" + e.getMessage() + ")");
            }

            // --- Step 3: WTS (optional — some maps have no localised strings) ---
            Map<String, String> namedEntries = Map.of();
            if (mpqEditor.hasFile("war3map.wts")) {
                try {
                    WTS wts = new WTS(new ByteArrayInputStream(
                            mpqEditor.extractFileAsBytes("war3map.wts")));
                    namedEntries = wts.getNamedEntries();
                } catch (Exception ignored) {
                    // WTS parse failure is non-fatal; raw strings will be used
                }
            }

            String gameVersion = StringUtils.buildGameVersionInfo(w3i);
            String editorVersion = String.valueOf(w3i.getEditorVersion());
            String name = resolveTrigStr(w3i.getMapName(), namedEntries, "<unknown>");
            String author = resolveTrigStr(w3i.getMapAuthor(), namedEntries, "<unknown>");
            String desc = resolveTrigStr(w3i.getMapDescription(), namedEntries, "<no description>");

            CameraBounds bounds = CameraBounds.getInstance();
            bounds.setCameraBounds(
                    w3i.getCameraBounds1(),
                    w3i.getCameraBounds2(),
                    w3i.getCameraBounds3(),
                    w3i.getCameraBounds4()
            );

            LOG.fine("Metadata loaded — name='" + name + "' author='" + author + "' gameVersion=" + gameVersion);
            return new MapMetadata(name, author, gameVersion, editorVersion, desc, previewBytes, bounds);
        }
    }

    /**
     * Validates war3mapUnits.doo header and rejects unsupported subversions.
     * This is intended to run at map-open time so the user gets immediate feedback.
     */
    public void validateDooUnitsSubversion(File mapFile) throws Exception {
        return; // Disable validation for now since some maps in the wild have very old DOO formats that we don't want to reject outright.  See
//        try (JMpqEditor mpq = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {
//            if (!mpq.hasFile(DOO_UNITS.GAME_PATH.getName())) return;
//
//            byte[] dooBytes = mpq.extractFileAsBytes(DOO_UNITS.GAME_PATH.getName());
//            if (dooBytes == null || dooBytes.length < DOO_UNITS_HEADER_SIZE) {
//                throw new IllegalArgumentException("war3mapUnits.doo is too short to contain a valid header.");
//            }
//
//            String magic = new String(dooBytes, 0, 4, StandardCharsets.US_ASCII);
//            if (!"W3do".equals(magic)) {
//                throw new IllegalArgumentException("Invalid war3mapUnits.doo header magic: " + magic);
//            }
//
//            int version = readUInt32LE(dooBytes, 4);
//            int subversion = readUInt32LE(dooBytes, 8);
//            int objectCount = readUInt32LE(dooBytes, 12);
//            LOG.fine("war3mapUnits.doo header: magic=" + magic
//                    + ", version=" + version
//                    + ", subversion=" + subversion
//                    + ", objectCount=" + objectCount);
//
//            if (subversion < MIN_DOO_UNITS_SUBVERSION) {
//                throw new IllegalArgumentException("Unsupported war3mapUnits.doo subversion: "
//                        + subversion + " (minimum supported: " + MIN_DOO_UNITS_SUBVERSION + ")."+
//                        "\nTry to place an item on the map and save it with the latest World Editor to update the DOO format.");
//            }
//        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reads all custom unit definitions from the map's {@code war3map.w3u} file and
     * returns them as a list of {@link UnitEntry} objects (id + display name).
     *
     * <p>Returns an empty list when the map has no custom units or the file cannot be parsed.
     * Never throws — all errors are swallowed and logged to stderr.
     *
     * @param mapFile the .w3x or .w3m map file
     * @return list of unit entries, possibly empty; never {@code null}
     */
    public List<UnitEntry> loadMapUnits(File mapFile) {
        LOG.info("Loading unit definitions: " + mapFile.getName());
        List<UnitEntry> entries = new ArrayList<>();
        try (JMpqEditor mpq = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {
            if (!mpq.hasFile("war3map.w3u")) return entries;

            W3U w3u = new W3U(new Wc3BinInputStream(
                    new ByteArrayInputStream(mpq.extractFileAsBytes("war3map.w3u"))));

            for (W3U.Obj obj : w3u.getObjsList()) {
                String id = obj.getId().toString();
                // get() returns the DataType directly for the field, or null if not set
                var nameVal = obj.get(MetaFieldId.valueOf("unam"));
                String name = (nameVal != null) ? nameVal.toString() : "";
                entries.add(new UnitEntry(id, name));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not load unit definitions from map: " + mapFile.getName(), e);
        }
        LOG.fine("Loaded " + entries.size() + " unit definition(s) from " + mapFile.getName());
        return entries;
    }

    /**
     * Reads all unit placements from {@code war3mapUnits.doo} and converts each
     * world-space position to normalised image coordinates [0..1] using the current
     * {@link CameraBounds} singleton (which must have been populated by a prior call
     * to {@link #loadMetadata}).
     *
     * <p>Returns an empty list when the file is absent, the camera bounds are not
     * initialised, or parsing fails.  Never throws.
     *
     * @param mapFile the .w3x or .w3m map file
     * @return list of existing unit placements in normalised image space, possibly empty
     */
    public List<ExistingUnit> loadExistingUnitPlacements(File mapFile) {
        LOG.info("Loading existing unit placements: " + mapFile.getName());
        List<ExistingUnit> result = new ArrayList<>();

        CameraBounds bounds = CameraBounds.getInstance();
        if (!bounds.isInitialized()) {
            LOG.warning("CameraBounds not initialised – skipping existing-unit overlay");
            return result;
        }

        // Derive the axis-aligned bounding box from the four corners
        float minX = bounds.getBottomLeft().getX().getVal();
        float maxX = bounds.getBottomRight().getX().getVal();
        float minY = bounds.getBottomLeft().getY().getVal();   // southern boundary (low Y)
        float maxY = bounds.getTopLeft().getY().getVal();      // northern boundary (high Y)
        float rangeX = maxX - minX;
        float rangeY = maxY - minY;
        if (rangeX <= 0 || rangeY <= 0) return result;

        try (JMpqEditor mpq = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {
            if (!mpq.hasFile(DOO_UNITS.GAME_PATH.getName())) return result;

            DOO_UNITS doo = new DOO_UNITS(new Wc3BinInputStream(
                    new ByteArrayInputStream(mpq.extractFileAsBytes(DOO_UNITS.GAME_PATH.getName()))));

            for (DOO_UNITS.Obj obj : doo.getObjs()) {
                Coords3DF pos = obj.getPos();
                float worldX = pos.getX().getVal();
                float worldY = pos.getY().getVal();

                // Convert world coords → normalised image coords
                // WC3: X increases east, Y increases north.
                // Image: X increases right (east), Y increases DOWN (south) → flip Y.
                double normX = (worldX - minX) / rangeX;
                double normY = 1.0 - (worldY - minY) / rangeY;

                // DOO_UNITS stores angles in degrees (same convention as the unit angle spinner)
                double angleDeg = Math.toDegrees(obj.getAngle());

                result.add(new ExistingUnit(normX, normY, angleDeg));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not load existing unit placements from " + mapFile.getName(), e);
        }

        LOG.fine("Loaded " + result.size() + " existing unit placement(s) from " + mapFile.getName());
        return result;
    }
}
