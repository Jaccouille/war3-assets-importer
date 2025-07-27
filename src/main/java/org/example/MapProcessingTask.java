package org.example;

import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class MapProcessingTask extends SwingWorker<Void, String> {

    private final File mapFile;
    private final File modelsFolder;
    private final String unitId = "hfoo"; // Example unit ID to add
    private final Consumer<String> logConsumer; // For passing logs back to UI

    public MapProcessingTask(File mapFile, File modelsFolder, Consumer<String> logConsumer) {
        this.mapFile = mapFile;
        this.modelsFolder = modelsFolder;
        this.logConsumer = logConsumer;
    }

    @Override
    protected Void doInBackground() {
        if (mapFile == null || modelsFolder == null) {
            log("Please select both a map and a models folder first.");
            return null;
        }

        log("Processing...");

        try {
            File parentDir = mapFile.getParentFile();
            File processedFile = new File(parentDir, "processed_" + mapFile.getName());

            File targetMap = Files.copy(mapFile.toPath(), processedFile.toPath(), StandardCopyOption.REPLACE_EXISTING).toFile();
            log("Copied map to: " + processedFile.getAbsolutePath());

            try (JMpqEditor mpqEditor = new JMpqEditor(targetMap, MPQOpenOption.FORCE_V0)) {
                log("Opened map: " + targetMap.getName());

//                W3U w3u = mpqEditor.hasFile("war3map.w3u")
//                        ? new W3U(new Wc3BinInputStream(new ByteArrayInputStream(mpqEditor.extractFileAsBytes("war3map.w3u"))))
//                        : new W3U();
//
//                mpqEditor.deleteFile("war3map.w3u");
//
//                w3u.addObj(ObjId.valueOf("x000"), ObjId.valueOf(unitId));
//                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
//                try (Wc3BinOutputStream out = new Wc3BinOutputStream(byteOut)) {
//                    w3u.write(out);
//                }
//
//                mpqEditor.insertByteArray("war3map.w3u", byteOut.toByteArray());

                Wc3MapAssetImporter.importAssetFiles(mpqEditor, modelsFolder);
            }

        } catch (Exception ex) {
            log("Error during processing: " + ex.getMessage());
            ex.printStackTrace();
        }

        log("Processing complete.");
        return null;
    }

    private void log(String message) {
        if (logConsumer != null) {
            publish(message); // Use publish() to trigger process() below
        }
    }

    @Override
    protected void process(java.util.List<String> chunks) {
        for (String msg : chunks) {
            logConsumer.accept(msg);
        }
    }
}
