package org.example;


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
import systems.crigges.jmpq3.JMpqEditor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Collectors;

public class Wc3MapAssetImporter {
//    public static void importAsset(File mapFile, File assetFile, String targetPathInMap, File outputFile) throws IOException {
//        // Step 1: Load the .w3x archive
//        MPQArchive archive = new MPQArchive(mapFile);
//
//        System.out.println("Opened map: " + mapFile.getName());
//
//        // Step 2: Read model file into bytes
//        byte[] assetBytes = Files.readAllBytes(assetFile.toPath());
//
//        // Step 3: Insert or replace file in archive
//        archive.addFile(targetPathInMap, assetBytes);
//        System.out.println("Added asset to map: " + targetPathInMap);
//
//        // Step 4: Save as new file
//        archive.writeTo(outputFile);
//        System.out.println("Saved updated map to: " + outputFile.getAbsolutePath());
//    }

    private static LinkedList<File> getFilesOfDirectory(File dir, LinkedList<File> addTo) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                getFilesOfDirectory(f, addTo);
            } else {
                addTo.add(f);
            }
        }
        return addTo;

    }

    private static void insertImportedFiles(JMpqEditor mpq, Set<Path> models, Path rootFolder) throws Exception {

//        DOO_UNITS dooUnits = mpq.hasFile(DOO_UNITS.GAME_PATH.toString())
//                ? new DOO_UNITS(new Wc3BinInputStream(new ByteArrayInputStream(mpq.extractFileAsBytes(DOO_UNITS.GAME_PATH.toString()))))
//                : new DOO_UNITS();
        DOO_UNITS dooUnits = new DOO_UNITS();

//        DOO_UNITS.Obj obj2 = dooUnits.addObj();
//        obj2.setTypeId(ObjId.valueOf("hfoo"));
//        obj2.setSkinId(ObjId.valueOf("hfoo"));
//        obj2.setPos(new Coords3DF(0, 0, 0));
//
//        mpq.deleteFile(DOO_UNITS.GAME_PATH.toString());
//        ByteArrayOutputStream byteArrayOutputStream2 = new ByteArrayOutputStream();
//        try (Wc3BinOutputStream wc3BinOutputStream = new Wc3BinOutputStream(byteArrayOutputStream2)) {
//            dooUnits.write(wc3BinOutputStream);
//        }
//        byteArrayOutputStream2.flush();
//        byte[] byteArray2 = byteArrayOutputStream2.toByteArray();
//        mpq.insertByteArray(DOO_UNITS.GAME_PATH.getName(), byteArray2);
//
//        if (true) {
//            return;
//        }

        IMP importFile = new IMP();
        W3U w3u = mpq.hasFile("war3map.w3u")
                ? new W3U(new Wc3BinInputStream(new ByteArrayInputStream(mpq.extractFileAsBytes("war3map.w3u"))))
                : new W3U();


        Set<String> existingIds = w3u.getObjsList().stream()
                .map(W3U.Obj::getId)
                .map(ObjId::toString)
                .collect(Collectors.toSet());


        String unitId = "hfoo";

//        w3u.addObj(ObjId.valueOf("x000"), ObjId.valueOf(unitId));
        Path baseFolderPath = rootFolder.toAbsolutePath().normalize();
        HashMap<String, Path> insertedTextures = new HashMap<>();

        Coords2DF topLeft = CameraBounds.getInstance().getTopLeft();
        Coords2DF bottomRight = CameraBounds.getInstance().getBottomRight();
        UnitPlacementGrid placer = new UnitPlacementGrid(topLeft, bottomRight, 128);

        for (Path modelPath : models) {
            Path filePath = baseFolderPath.relativize(modelPath);
            System.out.println("Processing file: " + filePath);

//                String normalizedWc3Path = p.toString().replaceAll("/", "\\\\");
            IMP.Obj importObj = new IMP.Obj();
            String insertedFilePath = filePath.toString();

            if (insertedTextures.containsKey(modelPath.getFileName().toString())) {
                System.out.println("Skipping already inserted texture: " + filePath);
                continue;
            }

            if (modelPath.getFileName().endsWith(".blp"))
                insertedFilePath = modelPath.getFileName().toString();

            importObj.setPath(insertedFilePath);
            importObj.setStdFlag(IMP.StdFlag.CUSTOM);
            importFile.addObj(importObj);
            mpq.deleteFile(insertedFilePath);
            mpq.insertFile(insertedFilePath, modelPath.toFile(), false);
            insertedTextures.put(modelPath.getFileName().toString(), modelPath);

            if (modelPath.getFileName().endsWith(".mdx")) {
                String idString = UnitIDGenerator.generateNextId(existingIds);
                existingIds.add(idString);
                ObjId newId = ObjId.valueOf(idString);
                System.out.println("Adding unit with ID: " + newId);
                ObjMod.Obj unitObj = w3u.addObj(newId, ObjId.valueOf(unitId));
                unitObj.set(MetaFieldId.valueOf("umdl"), new War3String(filePath.toString()));
                unitObj.set(MetaFieldId.valueOf("unam"), new War3String(modelPath.getFileName().toString().replaceAll(".mdx", "")));

                DOO_UNITS.Obj obj = dooUnits.addObj();
                obj.setTypeId(newId);
                obj.setSkinId(newId);

                Coords2DF coords2DF = placer.nextPosition();

                float x = coords2DF.getX().getVal();
                float y = coords2DF.getY().getVal();
                System.out.println("Placing unit at: " + x + ", " + y);

                obj.setPos(new Coords3DF(x, y, 0));
                obj.setAngle(270);
            }

        }
        mpq.deleteFile(IMP.GAME_PATH);
        mpq.deleteFile("war3map.w3u");
        mpq.deleteFile(DOO_UNITS.GAME_PATH.toString());
        // Write w3u
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (Wc3BinOutputStream wc3BinOutputStream = new Wc3BinOutputStream(byteArrayOutputStream)) {
            w3u.write(wc3BinOutputStream);
        }

        byteArrayOutputStream.flush();
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        mpq.insertByteArray("war3map.w3u", byteArray);

        // Write importFile
        byteArrayOutputStream = new ByteArrayOutputStream();
        try (Wc3BinOutputStream wc3BinOutputStream = new Wc3BinOutputStream(byteArrayOutputStream)) {
            importFile.write(wc3BinOutputStream);
        }
        byteArrayOutputStream.flush();
        byteArray = byteArrayOutputStream.toByteArray();
        mpq.insertByteArray(IMP.GAME_PATH, byteArray);

        // Write
        byteArrayOutputStream = new ByteArrayOutputStream();
        try (Wc3BinOutputStream wc3BinOutputStream = new Wc3BinOutputStream(byteArrayOutputStream)) {
            dooUnits.write(wc3BinOutputStream);
        }
        byteArrayOutputStream.flush();
        byteArray = byteArrayOutputStream.toByteArray();
        mpq.insertByteArray(DOO_UNITS.GAME_PATH.getName(), byteArray);

    }

    public static void importAssetFiles(JMpqEditor ed, Set<Path> filesToImport, Path rootFolder) {
        try {
            insertImportedFiles(ed, filesToImport, rootFolder);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}
