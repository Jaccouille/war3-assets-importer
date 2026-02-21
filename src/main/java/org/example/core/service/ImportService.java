package org.example.core.service;

import net.moonlightflower.wc3libs.bin.ObjMod;
import net.moonlightflower.wc3libs.bin.Wc3BinInputStream;
import net.moonlightflower.wc3libs.bin.Wc3BinOutputStream;
import net.moonlightflower.wc3libs.bin.app.DOO_UNITS;
import net.moonlightflower.wc3libs.bin.app.IMP;
import net.moonlightflower.wc3libs.bin.app.objMod.W3U;
import net.moonlightflower.wc3libs.dataTypes.app.Coords2DF;
import net.moonlightflower.wc3libs.dataTypes.app.Coords3DF;
import net.moonlightflower.wc3libs.dataTypes.app.War3String;
import net.moonlightflower.wc3libs.misc.MetaFieldId;
import net.moonlightflower.wc3libs.misc.ObjId;
import org.example.core.model.ImportOptions;
import org.example.core.model.ImportResult;
import org.example.core.util.CameraBounds;
import org.example.core.util.UnitIDGenerator;
import org.example.core.util.UnitPlacementGrid;
import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Core service that copies a Warcraft 3 map and inserts selected MDX/BLP assets into it.
 * This class contains no UI code and is shared by both the GUI and the CLI.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Copy the source map to {@code processed_<name>.w3x}</li>
 *   <li>Open the copy with JMPQ3</li>
 *   <li>Insert BLP textures into the MPQ import table</li>
 *   <li>For MDX files: generate a unique unit ID, add a W3U entry, place a DOO_UNITS instance</li>
 *   <li>Write all modified structures back to the MPQ</li>
 * </ul>
 */
public class ImportService {

    /**
     * Runs a full import operation.
     *
     * @param mapFile          source .w3x file (will NOT be modified — a copy is made)
     * @param selectedFiles    set of absolute paths to the assets to import
     * @param assetsRootFolder root of the assets folder (used to relativize paths)
     * @param options          user-selected import options
     * @param progressCallback receives log lines during processing; may be {@code null}
     * @return result containing success flag and all log messages
     */
    public ImportResult process(
            File mapFile,
            Set<Path> selectedFiles,
            File assetsRootFolder,
            ImportOptions options,
            Consumer<String> progressCallback
    ) {
        List<String> logs = new ArrayList<>();
        Consumer<String> log = msg -> {
            logs.add(msg);
            if (progressCallback != null) progressCallback.accept(msg);
        };

        if (mapFile == null || selectedFiles == null || selectedFiles.isEmpty()) {
            log.accept("Nothing to process: no map file or no selected assets.");
            return ImportResult.failure(logs);
        }

        try {
            // 1. Copy the source map
            File parentDir = mapFile.getParentFile();
            File processedFile = new File(parentDir, "processed_" + mapFile.getName());
            Files.copy(mapFile.toPath(), processedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.accept("Copied map to: " + processedFile.getAbsolutePath());

            // 2. Open the copy and run insertion
            try (JMpqEditor mpqEditor = new JMpqEditor(processedFile, MPQOpenOption.FORCE_V0)) {
                log.accept("Opened map: " + processedFile.getName());
                insertAssets(mpqEditor, selectedFiles, assetsRootFolder, options, log);
            }

            log.accept("Processing complete.");
            return ImportResult.success(logs);

        } catch (Exception ex) {
            log.accept("Error during processing: " + ex.getMessage());
            return ImportResult.failure(logs);
        }
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    private void insertAssets(
            JMpqEditor mpq,
            Set<Path> selectedFiles,
            File rootFolder,
            ImportOptions options,
            Consumer<String> log
    ) throws Exception {

        DOO_UNITS dooUnits = new DOO_UNITS();
        IMP importFile = new IMP();

        W3U w3u = mpq.hasFile("war3map.w3u")
                ? new W3U(new Wc3BinInputStream(new ByteArrayInputStream(mpq.extractFileAsBytes("war3map.w3u"))))
                : new W3U();

        Set<String> existingIds = w3u.getObjsList().stream()
                .map(W3U.Obj::getId)
                .map(ObjId::toString)
                .collect(Collectors.toSet());

        String baseUnitId = options.unitDefinition() != null ? options.unitDefinition() : "hfoo";

        Path baseFolderPath = rootFolder.toPath().toAbsolutePath().normalize();
        HashMap<String, File> insertedTextures = new HashMap<>();

        Coords2DF topLeft = CameraBounds.getInstance().getTopLeft();
        Coords2DF bottomRight = CameraBounds.getInstance().getBottomRight();
        UnitPlacementGrid placer = (topLeft != null && bottomRight != null)
                ? new UnitPlacementGrid(topLeft, bottomRight, 128)
                : null;

        for (Path absolutePath : selectedFiles) {
            File f = absolutePath.toFile();
            Path filePath = baseFolderPath.relativize(absolutePath.normalize());
            log.accept("Processing file: " + filePath);

            IMP.Obj importObj = new IMP.Obj();
            String insertedFilePath = filePath.toString();

            if (insertedTextures.containsKey(f.getName())) {
                log.accept("Skipping already inserted texture: " + filePath);
                continue;
            }

            // BLP textures are inserted by filename only (flat namespace in WC3)
            if (f.getName().toLowerCase().endsWith(".blp")) {
                insertedFilePath = f.getName();
            }

            importObj.setPath(insertedFilePath);
            importObj.setStdFlag(IMP.StdFlag.CUSTOM);
            importFile.addObj(importObj);
            mpq.deleteFile(insertedFilePath);
            mpq.insertFile(insertedFilePath, f, false);
            insertedTextures.put(f.getName(), f);

            // MDX files get a unit definition and a placed instance
            if (f.getName().toLowerCase().endsWith(".mdx") && options.createUnits()) {
                String idString = UnitIDGenerator.generateNextId(existingIds);
                existingIds.add(idString);
                ObjId newId = ObjId.valueOf(idString);
                log.accept("Adding unit with ID: " + newId);

                ObjMod.Obj unitObj = w3u.addObj(newId, ObjId.valueOf(baseUnitId));
                unitObj.set(MetaFieldId.valueOf("umdl"), new War3String(filePath.toString()));
                unitObj.set(MetaFieldId.valueOf("unam"), new War3String(
                        f.getName().replaceAll("(?i)\\.mdx$", "")));

                if (options.placeUnits() && placer != null) {
                    Coords2DF coords = placer.nextPosition();
                    if (coords != null) {
                        DOO_UNITS.Obj obj = dooUnits.addObj();
                        obj.setTypeId(newId);
                        obj.setSkinId(newId);
                        obj.setPos(new Coords3DF(coords.getX().getVal(), coords.getY().getVal(), 0));
                        obj.setAngle(270);
                        log.accept("Placed unit at: " + coords.getX().getVal() + ", " + coords.getY().getVal());
                    }
                }
            }
        }

        // Write all modified structures back to MPQ
        mpq.deleteFile(IMP.GAME_PATH);
        mpq.deleteFile("war3map.w3u");
        mpq.deleteFile(DOO_UNITS.GAME_PATH.toString());

        mpq.insertByteArray("war3map.w3u", serializeW3u(w3u));
        mpq.insertByteArray(IMP.GAME_PATH, serializeImp(importFile));
        mpq.insertByteArray(DOO_UNITS.GAME_PATH.getName(), serializeDooUnits(dooUnits));
    }

    private byte[] serializeW3u(W3U obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Wc3BinOutputStream out = new Wc3BinOutputStream(baos)) {
            obj.write(out);
        }
        return baos.toByteArray();
    }

    private byte[] serializeImp(IMP obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Wc3BinOutputStream out = new Wc3BinOutputStream(baos)) {
            obj.write(out);
        }
        return baos.toByteArray();
    }

    private byte[] serializeDooUnits(DOO_UNITS obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Wc3BinOutputStream out = new Wc3BinOutputStream(baos)) {
            obj.write(out);
        }
        return baos.toByteArray();
    }
}
