package org.example.core.service;

import net.moonlightflower.wc3libs.bin.app.W3I;
import net.moonlightflower.wc3libs.txt.WTS;
import org.example.core.model.MapMetadata;
import org.example.core.util.CameraBounds;
import org.example.core.util.StringUtils;
import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Reads Warcraft 3 map metadata (name, author, version, description, preview image,
 * camera bounds) from a .w3x/.w3m MPQ archive.
 * Used by both the GUI (to populate MapOptionsPanel) and the CLI (for informational output).
 */
public class MapMetadataService {

    /**
     * Opens the map MPQ, extracts W3I + WTS files, and returns a {@link MapMetadata} record.
     *
     * @param mapFile the .w3x or .w3m map file
     * @return parsed map metadata
     * @throws Exception if the MPQ cannot be read or required files are missing
     */
    public MapMetadata loadMetadata(File mapFile) throws Exception {
        try (JMpqEditor mpqEditor = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {
            W3I w3i = new W3I(mpqEditor.extractFileAsBytes("war3map.w3i"));
            WTS wts = new WTS(new ByteArrayInputStream(mpqEditor.extractFileAsBytes("war3map.wts")));

            Map<String, String> namedEntries = wts.getNamedEntries();

            byte[] previewBytes = null;
            if (mpqEditor.hasFile("war3mapMap.blp")) {
                previewBytes = mpqEditor.extractFileAsBytes("war3mapMap.blp");
            }

            String gameVersion = StringUtils.buildGameVersionInfo(w3i);
            String editorVersion = String.valueOf(w3i.getEditorVersion());
            String name   = resolveTrigStr(w3i.getMapName(),        namedEntries, "<unknown>");
            String author = resolveTrigStr(w3i.getMapAuthor(),      namedEntries, "<unknown>");
            String desc   = resolveTrigStr(w3i.getMapDescription(), namedEntries, "<no description>");

            CameraBounds bounds = CameraBounds.getInstance();
            bounds.setCameraBounds(
                    w3i.getCameraBounds1(),
                    w3i.getCameraBounds2(),
                    w3i.getCameraBounds3(),
                    w3i.getCameraBounds4()
            );

            return new MapMetadata(name, author, gameVersion, editorVersion, desc, previewBytes, bounds);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
     * @return             the resolved, trimmed display string
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
            } catch (NumberFormatException ignored) {}

            // 2c. Fallback: the TRIGSTR token itself is the best we can show
            return raw;
        }

        // 3. Not a TRIGSTR reference — it is the literal string value
        return raw.trim();
    }
}
