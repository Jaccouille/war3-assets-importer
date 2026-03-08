package com.hiveworkshop.war3assetsimporter.core.service;

import com.hiveworkshop.war3assetsimporter.core.model.ImportOptions;
import com.hiveworkshop.war3assetsimporter.core.model.ImportResult;
import com.hiveworkshop.war3assetsimporter.core.util.CameraBounds;
import com.hiveworkshop.war3assetsimporter.core.util.UnitIDGenerator;
import com.hiveworkshop.war3assetsimporter.core.util.UnitPlacementGrid;
import com.hiveworkshop.war3assetsimporter.gui.NameFormatter;
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
import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
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

    /** Maps alternate-animation sequence keyword → the value to write into the {@code uani} field. */
    private static final Map<String, String> ALTERNATE_ANIM_UANI = new LinkedHashMap<>();
    static {
        ALTERNATE_ANIM_UANI.put("Alternate",      "alternate");
        ALTERNATE_ANIM_UANI.put("Upgrade First",  "upgrade,first");
        ALTERNATE_ANIM_UANI.put("Upgrade Second", "upgrade,second");
        ALTERNATE_ANIM_UANI.put("Upgrade Third",  "upgrade,third");
    }

    // Markers used to bracket the generated JASS block so re-imports replace it in place
    private static final String JASS_SECTION_BEGIN = "// === War3AssetsImporter BEGIN ===";
    private static final String JASS_SECTION_END = "// === War3AssetsImporter END ===";
    private static final String JASS_FUNCTION_NAME = "CreateImportedAssetUnits";
    private static final String JASS_FUNCTION_CALL = "call " + JASS_FUNCTION_NAME + "()";
    private static final java.util.Set<String> TEXTURE_EXTENSIONS = new java.util.HashSet<>(
            java.util.Arrays.asList(".blp", ".dds", ".tga", ".png", ".jpg", ".jpeg", ".bmp", ".gif"));

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    private static boolean isTextureFile(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && TEXTURE_EXTENSIONS.contains(filename.substring(dot).toLowerCase());
    }

    /**
     * Resolves the in-MPQ path for a file being imported.
     *
     * <ul>
     *   <li>BTN* textures  → {@code ReplaceableTextures\CommandButtons\<filename>}</li>
     *   <li>DISBTN* textures → {@code ReplaceableTextures\CommandButtonsDisabled\<filename>}</li>
     *   <li>Other textures  → flat namespace ({@code <filename>} only)</li>
     *   <li>Non-textures with flattenPaths → {@code <filename>} only</li>
     *   <li>Non-textures otherwise → {@code relativePath} (folder structure preserved)</li>
     * </ul>
     *
     * @param filename     file name only (e.g. {@code BTNMyUnit.blp})
     * @param relativePath relative path from the assets root (used for non-texture files);
     *                     may be {@code null} when only the icon path is needed
     * @param options      current import options
     */
    private static String resolveInsertedPath(String filename, String relativePath,
                                              ImportOptions options) {
        if (isTextureFile(filename)) {
            String lower = filename.toLowerCase();
            if (lower.startsWith("disbtn")) {
                return "ReplaceableTextures\\CommandButtonsDisabled\\" + filename;
            }
            if (lower.startsWith("btn")) {
                return "ReplaceableTextures\\CommandButtons\\" + filename;
            }
            return filename; // other textures: flat namespace
        }
        return (options.getFlattenPaths() || relativePath == null) ? filename : relativePath;
    }

    /**
     * Reads {@code war3map.j} from the MPQ, injects (or replaces) the
     * {@value #JASS_FUNCTION_NAME} block delimited by {@link #JASS_SECTION_BEGIN} /
     * {@link #JASS_SECTION_END} markers, ensures the function is called from
     * {@code main}, and writes the result back.
     */
    private static void updateWarcraft3Script(JMpqEditor mpq,
                                              List<UnitPlacement> placements,
                                              Consumer<String> log) {
        if (!mpq.hasFile("war3map.j")) {
            log.accept("Warning: war3map.j not found in map — cannot inject unit creation calls.");
            return;
        }
        try {
            String script = new String(mpq.extractFileAsBytes("war3map.j"), StandardCharsets.UTF_8);

            // Build the new function block
            String block = buildJassFunctionBlock(placements);

            // Replace existing section or inject before `function main`
            if (script.contains(JASS_SECTION_BEGIN)) {
                int start = script.indexOf(JASS_SECTION_BEGIN);
                int end = script.indexOf(JASS_SECTION_END, start);
                if (end != -1) {
                    end += JASS_SECTION_END.length();
                    if (end < script.length() && script.charAt(end) == '\n') end++;
                    script = script.substring(0, start) + block + script.substring(end);
                }
            } else {
                // First import: find `function main` and insert just before it
                int mainIdx = script.indexOf("\nfunction main ");
                if (mainIdx == -1) mainIdx = script.indexOf("\nfunction main\t");
                if (mainIdx != -1) {
                    script = script.substring(0, mainIdx + 1) + block + "\n"
                            + script.substring(mainIdx + 1);
                } else {
                    script = script + "\n" + block;
                }
            }

            // Ensure the function is called — insert after `call CreateAllUnits`
            if (!script.contains(JASS_FUNCTION_CALL)) {
                int idx = script.indexOf("call CreateAllUnits");
                if (idx != -1) {
                    int lineEnd = script.indexOf('\n', idx);
                    if (lineEnd == -1) lineEnd = script.length() - 1;
                    script = script.substring(0, lineEnd + 1)
                            + "    " + JASS_FUNCTION_CALL + "\n"
                            + script.substring(lineEnd + 1);
                } else {
                    log.accept("Warning: 'call CreateAllUnits' not found in war3map.j — "
                            + "add 'call " + JASS_FUNCTION_NAME + "()' to main manually.");
                }
            }

            byte[] bytes = script.getBytes(StandardCharsets.UTF_8);
            mpq.deleteFile("war3map.j");
            mpq.insertByteArray("war3map.j", bytes);
            log.accept("Updated war3map.j with " + placements.size()
                    + " BlzCreateUnitWithSkin call(s).");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to update war3map.j", ex);
            log.accept("Warning: could not update war3map.j — " + ex.getMessage());
        }
    }

    /**
     * Generates the JASS function block wrapped in section markers.
     */
    private static String buildJassFunctionBlock(List<UnitPlacement> placements) {
        StringBuilder sb = new StringBuilder();
        sb.append(JASS_SECTION_BEGIN).append("\n");
        sb.append("function ").append(JASS_FUNCTION_NAME).append(" takes nothing returns nothing\n");
        if (!placements.isEmpty()) {
            sb.append("    local player p = Player(0)\n");
            sb.append("    local unit u\n");
            for (UnitPlacement pl : placements) {
                sb.append(String.format(
                        "    set u = BlzCreateUnitWithSkin( p, '%s', %.3f, %.3f, %.3f, '%s' )\n",
                        pl.id(), pl.x(), pl.y(), pl.angle(), pl.id()));
            }
        }
        sb.append("endfunction\n");
        sb.append(JASS_SECTION_END).append("\n");
        return sb.toString();
    }

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

        Set<String> existingImpPaths = importFile.getObjs().stream()
                .map(IMP.Obj::getPath)
                .collect(Collectors.toCollection(HashSet::new));

        W3U w3u = mpq.hasFile("war3map.w3u")
                ? new W3U(new Wc3BinInputStream(new ByteArrayInputStream(mpq.extractFileAsBytes("war3map.w3u"))))
                : new W3U();

        Set<String> existingIds = w3u.getObjsList().stream()
                .map(W3U.Obj::getId)
                .map(ObjId::toString)
                .collect(Collectors.toSet());

        Set<String> existingModelPaths = w3u.getObjsList().stream()
                .map(obj -> {
                    try {
                        // Check if the field exists to avoid exceptions for non-unit objects
                        Object val = obj.get(MetaFieldId.valueOf("umdl"));
                        return val != null ? val.toString() : null;
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(s -> s != null)
                .collect(Collectors.toCollection(HashSet::new));

        String baseUnitId = options.getUnitOriginId() != null ? options.getUnitOriginId() : "hfoo";

        Path baseFolderPath = rootFolder.toPath().toAbsolutePath().normalize();
        HashMap<String, File> insertedTextures = new HashMap<>();

        // Build a lookup from model base name (lowercase) -> full MPQ path of the BTN icon.
        // Icons are texture files whose name starts with "BTN"; the rest of the name (minus extension)
        // is matched against the MDX filename (minus extension).
        Map<String, String> iconByModelName = new HashMap<>();
        if (options.getAutoAssignIcon()) {
            for (Path p : selectedFiles) {
                String name = p.toFile().getName();
                if (name.toLowerCase().startsWith("btn") && isTextureFile(name)) {
                    int dot = name.lastIndexOf('.');
                    String key = name.substring(3, dot).toLowerCase();
                    // Store the full MPQ path so uico points to where the icon actually lives
                    iconByModelName.put(key, resolveInsertedPath(name, null, options));
                }
            }
        }

        // Prefer the user-drawn shape bounds (world coords + world spacing already computed).
        // Fall back to full camera bounds with the raw screen-pixel spacing when no shape was drawn.
        ImportOptions.PlacementBounds pb = options.getPlacementBounds();
        ImportOptions.PlacingOrder placingOrder = options.getPlacingOrder();
        UnitPlacementGrid placer;
        if (pb != null) {
            placer = new UnitPlacementGrid(pb.minX(), pb.maxX(), pb.minY(), pb.maxY(),
                    pb.spacingX(), pb.spacingY(), placingOrder);
            log.accept(String.format("Using drawn placement area: (%.0f,%.0f)→(%.0f,%.0f), spacing=(%.1f,%.1f), order=%s",
                    pb.minX(), pb.minY(), pb.maxX(), pb.maxY(), pb.spacingX(), pb.spacingY(), placingOrder));
        } else {
            Coords2DF topLeft = CameraBounds.getInstance().getTopLeft();
            Coords2DF bottomRight = CameraBounds.getInstance().getBottomRight();
            float spacingX = (float) options.getUnitSpacingX();
            float spacingY = (float) options.getUnitSpacingY();
            placer = (topLeft != null && bottomRight != null)
                    ? new UnitPlacementGrid(topLeft, bottomRight, spacingX, spacingY, placingOrder)
                    : null;
            log.accept("No placement shape drawn — falling back to camera bounds.");
        }

        int total = selectedFiles.size();
        int[] done = {0};
        List<UnitPlacement> jassUnitPlacements = new ArrayList<>();

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

            String insertedFilePath = resolveInsertedPath(f.getName(), filePath.toString(), options);

            if (insertedTextures.containsKey(f.getName())) {
                log.accept("Skipping already inserted texture: " + filePath);
                continue;
            }

            if (!existingImpPaths.contains(insertedFilePath)) {
                IMP.Obj importObj = new IMP.Obj();
                importObj.setPath(insertedFilePath);
                importObj.setStdFlag(IMP.StdFlag.CUSTOM);
                importFile.addObj(importObj);
                existingImpPaths.add(insertedFilePath);
            } else {
                log.accept("Import entry already exists, skipping IMP: " + insertedFilePath);
            }

            mpq.deleteFile(insertedFilePath);
            mpq.insertFile(insertedFilePath, f, false);
            insertedTextures.put(f.getName(), f);

            // MDX files get a unit definition and a placed instance.
            // Portrait files (_Portrait.mdx) are skipped — they have no in-game unit.
            boolean isPortrait = f.getName().toLowerCase().endsWith("_portrait.mdx");
            if (f.getName().toLowerCase().endsWith(".mdx") && !isPortrait && options.getCreateUnits()) {
                String modelPath = options.getFlattenPaths() ? f.getName() : filePath.toString();
                if (existingModelPaths.contains(modelPath)) {
                    log.accept("Unit already defined for model: " + modelPath + ", skipping.");
                } else {
                    // Resolve display name once — shared by base and all alternate unit definitions
                    String unitName;
                    if (options.getAutoNameUnits()) {
                        String baseFilename = f.getName().replaceAll("(?i)\\.mdx$", "");
                        unitName = NameFormatter.format(baseFilename, options.getNameFormat());
                        log.accept("Formatted unit name: " + baseFilename + " -> " + unitName);
                    } else {
                        unitName = f.getName().replaceAll("(?i)\\.mdx$", "");
                    }

                    String modelBaseName = f.getName().replaceAll("(?i)\\.mdx$", "").toLowerCase();
                    String iconFilename = iconByModelName.get(modelBaseName);

                    // ---- Base unit definition ----
                    String idString = UnitIDGenerator.generateNextId(existingIds);
                    existingIds.add(idString);
                    ObjId newId = ObjId.valueOf(idString);
                    log.accept("Adding unit with ID: " + newId);

                    ObjMod.Obj unitObj = w3u.addObj(newId, ObjId.valueOf(baseUnitId));
                    unitObj.set(MetaFieldId.valueOf("usca"), new War3Real((float) options.getUnitScaling()));
                    unitObj.set(MetaFieldId.valueOf("umdl"), new War3String(modelPath));
                    unitObj.set(MetaFieldId.valueOf("unam"), new War3String(unitName));
                    if (iconFilename != null) {
                        unitObj.set(MetaFieldId.valueOf("uico"), new War3String(iconFilename));
                        log.accept("Assigned icon: " + iconFilename);
                    }

                    existingModelPaths.add(modelPath);

                    if (options.getPlaceUnits() && placer != null) {
                        Coords2DF coords = placer.nextPosition();
                        if (coords != null) {
                            float cx = coords.getX().getVal();
                            float cy = coords.getY().getVal();
                            if (!dooUnitsParseFailed) {
                                DOO_UNITS.Obj dooUnitObj = dooUnits.addObj();
                                dooUnitObj.setTypeId(newId);
                                dooUnitObj.setSkinId(newId);
                                dooUnitObj.setPos(new Coords3DF(cx, cy, 0));
                                dooUnitObj.setAngle((float) Math.toRadians(options.getUnitAngle()));
                                dooUnitObj.setScale(new Coords3DF(1F, 1F, 1F));
                                dooUnitObj.setLifePerc(-1);
                                dooUnitObj.setManaPerc(-1);
                            }
                            jassUnitPlacements.add(new UnitPlacement(idString, cx, cy, options.getUnitAngle()));
                            log.accept("Placed unit at: " + cx + ", " + cy);
                        }
                    }

                    // ---- Alternate-animation unit definitions ----
                    // Scan the MDX file for alternate/upgrade animation sequences and create
                    // one additional unit definition per detected keyword, with uani set.
                    List<String> alternateAnims = options.getCreateAlternateUnits()
                            ? MdxAnimationScanner.scan(f) : List.of();
                    for (String keyword : alternateAnims) {
                        String uaniValue = ALTERNATE_ANIM_UANI.get(keyword);
                        if (uaniValue == null) continue;

                        String altIdString = UnitIDGenerator.generateNextId(existingIds);
                        existingIds.add(altIdString);
                        ObjId altId = ObjId.valueOf(altIdString);
                        log.accept("Adding alternate unit [" + keyword + "] with ID: " + altId);

                        ObjMod.Obj altObj = w3u.addObj(altId, ObjId.valueOf(baseUnitId));
                        altObj.set(MetaFieldId.valueOf("usca"), new War3Real((float) options.getUnitScaling()));
                        altObj.set(MetaFieldId.valueOf("umdl"), new War3String(modelPath));
                        altObj.set(MetaFieldId.valueOf("unam"), new War3String(unitName + " " + keyword));
                        altObj.set(MetaFieldId.valueOf("uani"), new War3String(uaniValue));
                        if (iconFilename != null) {
                            altObj.set(MetaFieldId.valueOf("uico"), new War3String(iconFilename));
                        }

                        if (options.getPlaceUnits() && placer != null) {
                            Coords2DF coords = placer.nextPosition();
                            if (coords != null) {
                                float cx = coords.getX().getVal();
                                float cy = coords.getY().getVal();
                                if (!dooUnitsParseFailed) {
                                    DOO_UNITS.Obj dooUnitObj = dooUnits.addObj();
                                    dooUnitObj.setTypeId(altId);
                                    dooUnitObj.setSkinId(altId);
                                    dooUnitObj.setPos(new Coords3DF(cx, cy, 0));
                                    dooUnitObj.setAngle((float) Math.toRadians(options.getUnitAngle()));
                                    dooUnitObj.setScale(new Coords3DF(1F, 1F, 1F));
                                    dooUnitObj.setLifePerc(-1);
                                    dooUnitObj.setManaPerc(-1);
                                }
                                jassUnitPlacements.add(new UnitPlacement(altIdString, cx, cy, options.getUnitAngle()));
                                log.accept("Placed alternate unit [" + keyword + "] at: " + cx + ", " + cy);
                            }
                        }
                    }
                }
            }
        }

        // Update war3map.j with BlzCreateUnitWithSkin calls when units are placed,
        // or clear the section when clearUnits is requested.
        if ((options.getCreateUnits() && options.getPlaceUnits()) || options.getClearUnits()) {
            List<UnitPlacement> scriptPlacements = options.getClearUnits() ? List.of() : jassUnitPlacements;
            updateWarcraft3Script(mpq, scriptPlacements, log);
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

    // -------------------------------------------------------------------------
    // war3map.j injection
    // -------------------------------------------------------------------------

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

    private record UnitPlacement(String id, float x, float y, float angle) {
    }
}
