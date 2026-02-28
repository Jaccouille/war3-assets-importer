package org.example.core.service;

import net.moonlightflower.wc3libs.bin.ObjMod;
import net.moonlightflower.wc3libs.bin.Wc3BinInputStream;
import net.moonlightflower.wc3libs.bin.Wc3BinOutputStream;
import net.moonlightflower.wc3libs.bin.app.DOO_UNITS;
import net.moonlightflower.wc3libs.bin.app.IMP;
import net.moonlightflower.wc3libs.bin.app.objMod.W3U;
import net.moonlightflower.wc3libs.dataTypes.app.Coords2DF;
import net.moonlightflower.wc3libs.dataTypes.app.Coords3DF;
import net.moonlightflower.wc3libs.dataTypes.app.War3Real;
import net.moonlightflower.wc3libs.dataTypes.app.War3String;
import net.moonlightflower.wc3libs.misc.MetaFieldId;
import net.moonlightflower.wc3libs.misc.ObjId;
import org.example.core.model.ImportOptions;
import org.example.core.model.ImportResult;
import org.example.core.util.CameraBounds;
import org.example.core.util.UnitIDGenerator;
import org.example.core.util.UnitPlacementGrid;
import org.example.gui.NameFormatter;
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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Core service that copies a Warcraft 3 map and inserts selected MDX/BLP assets into it.
 * This class contains no UI code and is shared by both the GUI and the CLI.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Copy the source map to the caller-specified output file</li>
 *   <li>Open the copy with JMPQ3</li>
 *   <li>Insert BLP textures into the MPQ import table</li>
 *   <li>For MDX files: generate a unique unit ID, add a W3U entry, place a DOO_UNITS instance</li>
 *   <li>Write all modified structures back to the MPQ</li>
 * </ul>
 */
public class ImportService {

    private static final Logger LOG = Logger.getLogger(ImportService.class.getName());

    /**
     * Runs a full import operation.
     *
     * @param mapFile          source .w3x file (will NOT be modified — a copy is made)
     * @param outputFile       destination file to write; created/overwritten by this call
     * @param selectedFiles    set of absolute paths to the assets to import
     * @param assetsRootFolder root of the assets folder (used to relativize paths)
     * @param options          user-selected import options
     * @param progressCallback receives log lines during processing; may be {@code null}
     * @param percentCallback  receives progress as an integer 0–100; may be {@code null}
     * @return result containing success flag and all log messages
     */
    public ImportResult process(
            File mapFile,
            File outputFile,
            Set<Path> selectedFiles,
            File assetsRootFolder,
            ImportOptions options,
            Consumer<String> progressCallback,
            Consumer<Integer> percentCallback
    ) {
        LOG.info("Import started: map=" + (mapFile != null ? mapFile.getName() : "null")
                + " output=" + (outputFile != null ? outputFile.getName() : "null")
                + " assets=" + (selectedFiles != null ? selectedFiles.size() : 0));
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
            if (percentCallback != null) percentCallback.accept(0);

            // 1. Copy the source map to the requested output path
            Files.copy(mapFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.accept("Copied map to: " + outputFile.getAbsolutePath());
            if (percentCallback != null) percentCallback.accept(10);

            // 2. Open the copy and run insertion
            try (JMpqEditor mpqEditor = new JMpqEditor(outputFile, MPQOpenOption.FORCE_V0)) {
                log.accept("Opened map: " + outputFile.getName());
                insertAssets(mpqEditor, selectedFiles, assetsRootFolder, options, log, percentCallback);
            }

            log.accept("Processing complete.");
            if (percentCallback != null) percentCallback.accept(100);
            LOG.info("Import finished successfully: " + outputFile.getAbsolutePath());
            return ImportResult.success(logs);

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Import failed", ex);
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
            Consumer<String> log,
            Consumer<Integer> percentCallback
    ) throws Exception {
        LOG.fine("insertAssets: " + selectedFiles.size() + " file(s), createUnits=" + options.getCreateUnits()
                + " placeUnits=" + options.getPlaceUnits() + " baseUnit=" + options.getUnitOriginId());

        // ---- Load or create DOO_UNITS ----
        // If parsing unexpectedly fails we skip writing DOO_UNITS back so the existing
        // terrain units are left untouched in the output MPQ.
        DOO_UNITS dooUnits;
        boolean dooUnitsParseFailed = false;
        if (options.getClearUnits()) {
            dooUnits = new DOO_UNITS();
            log.accept("Cleared existing units from map.");
        } else {
            if (mpq.hasFile(DOO_UNITS.GAME_PATH.getName())) {
                try {
                    dooUnits = new DOO_UNITS(new Wc3BinInputStream(
                            new ByteArrayInputStream(mpq.extractFileAsBytes(DOO_UNITS.GAME_PATH.getName()))));
                    log.accept("Loaded existing units from map (" + dooUnits.getObjs().size() + " units).");
                } catch (Exception dooEx) {
                    LOG.log(Level.WARNING, "Could not parse war3mapUnits.doo — existing terrain units will be preserved unchanged.", dooEx);
                    log.accept("Warning: could not read existing terrain units. Existing terrain units will NOT be overwritten.");
                    dooUnits = new DOO_UNITS(); // placeholder — will not be written back
                    dooUnitsParseFailed = true;
                }
            } else {
                dooUnits = new DOO_UNITS();
            }
        }

        // ---- Load or create IMP ----
        IMP importFile;
        if (options.getClearAssets()) {
            importFile = new IMP();
            log.accept("Cleared existing import entries from map.");
        } else {
            if (mpq.hasFile(IMP.GAME_PATH)) {
                importFile = new IMP(new Wc3BinInputStream(
                        new ByteArrayInputStream(mpq.extractFileAsBytes(IMP.GAME_PATH))));
                log.accept("Loaded existing import entries from map (" + importFile.getObjs().size() + " entries).");
            } else {
                importFile = new IMP();
            }
        }

        W3U w3u = mpq.hasFile("war3map.w3u")
                ? new W3U(new Wc3BinInputStream(new ByteArrayInputStream(mpq.extractFileAsBytes("war3map.w3u"))))
                : new W3U();

        Set<String> existingIds = w3u.getObjsList().stream()
                .map(W3U.Obj::getId)
                .map(ObjId::toString)
                .collect(Collectors.toSet());

        String baseUnitId = options.getUnitOriginId() != null ? options.getUnitOriginId() : "hfoo";

        Path baseFolderPath = rootFolder.toPath().toAbsolutePath().normalize();
        HashMap<String, File> insertedTextures = new HashMap<>();

        // Prefer the user-drawn shape bounds (world coords + world spacing already computed).
        // Fall back to full camera bounds with the raw screen-pixel spacing when no shape was drawn.
        ImportOptions.PlacementBounds pb = options.getPlacementBounds();
        UnitPlacementGrid placer;
        if (pb != null) {
            placer = new UnitPlacementGrid(pb.minX(), pb.maxX(), pb.minY(), pb.maxY(),
                    pb.spacingX(), pb.spacingY());
            log.accept(String.format("Using drawn placement area: (%.0f,%.0f)→(%.0f,%.0f), spacing=(%.1f,%.1f)",
                    pb.minX(), pb.minY(), pb.maxX(), pb.maxY(), pb.spacingX(), pb.spacingY()));
        } else {
            Coords2DF topLeft = CameraBounds.getInstance().getTopLeft();
            Coords2DF bottomRight = CameraBounds.getInstance().getBottomRight();
            float spacingX = (float) options.getUnitSpacingX();
            float spacingY = (float) options.getUnitSpacingY();
            placer = (topLeft != null && bottomRight != null)
                    ? new UnitPlacementGrid(topLeft, bottomRight, spacingX, spacingY)
                    : null;
            log.accept("No placement shape drawn — falling back to camera bounds.");
        }

        int total = selectedFiles.size();
        int[] done = {0};

        for (Path absolutePath : selectedFiles) {
            File f = absolutePath.toFile();
            Path filePath = baseFolderPath.relativize(absolutePath.normalize());
            log.accept("Processing file: " + filePath);
            done[0]++;
            if (percentCallback != null) {
                // Files occupy 10%→100% of the total progress range
                int pct = 10 + (int) (90.0 * done[0] / total);
                percentCallback.accept(Math.min(pct, 99)); // 100 is reserved for full success
            }

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
            if (f.getName().toLowerCase().endsWith(".mdx") && options.getCreateUnits()) {
                String idString = UnitIDGenerator.generateNextId(existingIds);
                existingIds.add(idString);
                ObjId newId = ObjId.valueOf(idString);
                log.accept("Adding unit with ID: " + newId);

                ObjMod.Obj unitObj = w3u.addObj(newId, ObjId.valueOf(baseUnitId));
                unitObj.set(MetaFieldId.valueOf("usca"), new War3Real((float) options.getUnitScaling())); // where unit scaling is a double, e.g 1.0
                unitObj.set(MetaFieldId.valueOf("umdl"), new War3String(filePath.toString()));

                // Format the unit name
                String unitName;
                if (options.getAutoNameUnits()) {
                    String baseFilename = f.getName().replaceAll("(?i)\\.mdx$", "");
                    unitName = NameFormatter.format(baseFilename, options.getNameFormat());
                    log.accept("Formatted unit name: " + baseFilename + " -> " + unitName);
                } else {
                    unitName = f.getName().replaceAll("(?i)\\.mdx$", "");
                }
                unitObj.set(MetaFieldId.valueOf("unam"), new War3String(unitName));

                if (options.getPlaceUnits() && placer != null && !dooUnitsParseFailed) {
                    Coords2DF coords = placer.nextPosition();
                    if (coords != null) {
                        DOO_UNITS.Obj dooUnitObj = dooUnits.addObj();
                        dooUnitObj.setTypeId(newId);
                        dooUnitObj.setSkinId(newId);
                        dooUnitObj.setPos(new Coords3DF(coords.getX().getVal(), coords.getY().getVal(), 0));
                        dooUnitObj.setAngle((float) Math.toRadians(options.getUnitAngle()));
                        dooUnitObj.setScale(new Coords3DF(1F, 1F, 1F));
                        dooUnitObj.setLifePerc(-1);  // -1 = use unit's default max health
                        dooUnitObj.setManaPerc(-1);  // -1 = use unit's default max mana
                        log.accept("Placed unit at: " + coords.getX().getVal() + ", " + coords.getY().getVal());
                    }
                }
            }
        }

        // Write all modified structures back to MPQ
        mpq.deleteFile(IMP.GAME_PATH);
        mpq.deleteFile("war3map.w3u");

        mpq.insertByteArray("war3map.w3u", serializeW3u(w3u));
        mpq.insertByteArray(IMP.GAME_PATH, serializeImp(importFile));

        // Only rewrite DOO_UNITS if we successfully read (or intentionally cleared) it.
        // If parsing failed, leave the existing binary untouched so the map's terrain units are preserved.
        if (!dooUnitsParseFailed) {
            mpq.deleteFile(DOO_UNITS.GAME_PATH.getName());
            mpq.insertByteArray(DOO_UNITS.GAME_PATH.getName(), serializeDooUnits(dooUnits));
        } else {
            LOG.fine("Skipping war3mapUnits.doo rewrite — preserving original binary in MPQ.");
            log.accept("Skipped war3mapUnits.doo rewrite — original terrain units preserved in output map.");
        }
    }

    private byte[] serializeW3u(W3U obj) throws Exception {
        LOG.fine("Serializing W3U (" + obj.getObjsList().size() + " custom unit(s))");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Wc3BinOutputStream out = new Wc3BinOutputStream(baos)) {
            obj.write(out);
        }
        return baos.toByteArray();
    }

    private byte[] serializeImp(IMP obj) throws Exception {
        LOG.fine("Serializing IMP (" + obj.getObjs().size() + " import entry/entries)");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Wc3BinOutputStream out = new Wc3BinOutputStream(baos)) {
            obj.write(out);
        }
        return baos.toByteArray();
    }

    private byte[] serializeDooUnits(DOO_UNITS obj) throws Exception {
        LOG.fine("Serializing DOO_UNITS (" + obj.getObjs().size() + " placed unit(s))");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Wc3BinOutputStream out = new Wc3BinOutputStream(baos)) {
            obj.write(out);
        }
        return baos.toByteArray();
    }
}
