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
            String name = namedEntries.getOrDefault(w3i.getMapName(), "<unknown>");
            String author = namedEntries.getOrDefault(w3i.getMapAuthor(), "<unknown>");
            String desc = namedEntries.getOrDefault(w3i.getMapDescription(), "<no description>");

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
}
