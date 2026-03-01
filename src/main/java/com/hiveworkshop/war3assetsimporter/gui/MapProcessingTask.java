package com.hiveworkshop.war3assetsimporter.gui;

import com.hiveworkshop.war3assetsimporter.core.model.ImportOptions;
import com.hiveworkshop.war3assetsimporter.core.model.ImportResult;
import com.hiveworkshop.war3assetsimporter.core.service.ImportService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SwingWorker that runs {@link ImportService#process} on a background thread,
 * forwards progress log lines to the EDT via {@link #process(List)}, and reports
 * completion percentage via {@link #setProgress(int)} so the status bar can be updated.
 */
public class MapProcessingTask extends SwingWorker<ImportResult, String> {

    private static final Logger LOG = Logger.getLogger(MapProcessingTask.class.getName());

    private final File mapFile;
    private final File outputFile;
    private final Set<Path> selectedFiles;
    private final File assetsRootFolder;
    private final ImportOptions options;
    private final ImportService importService;
    private final Consumer<String> logConsumer;
    private final JFrame owner;

    public MapProcessingTask(
            File mapFile,
            File outputFile,
            Set<Path> selectedFiles,
            File assetsRootFolder,
            ImportOptions options,
            ImportService importService,
            Consumer<String> logConsumer,
            JFrame owner
    ) {
        this.mapFile = mapFile;
        this.outputFile = outputFile;
        this.selectedFiles = selectedFiles;
        this.assetsRootFolder = assetsRootFolder;
        this.options = options;
        this.importService = importService;
        this.logConsumer = logConsumer;
        this.owner = owner;
    }

    /**
     * Returns the last log line that starts with "Error", or the last line overall.
     */
    private static String lastError(List<String> logs) {
        if (logs == null || logs.isEmpty()) return "Unknown error.";
        for (int i = logs.size() - 1; i >= 0; i--) {
            if (logs.get(i).startsWith("Error")) return logs.get(i);
        }
        return logs.get(logs.size() - 1);
    }

    @Override
    protected ImportResult doInBackground() {
        LOG.info("Import task started: map=" + mapFile.getName()
                + " output=" + outputFile.getName()
                + " files=" + selectedFiles.size());
        return importService.process(
                mapFile,
                outputFile,
                selectedFiles,
                assetsRootFolder,
                options,
                msg -> publish(msg),           // log lines — routed through process() on EDT
                pct -> setProgress(pct)        // percent 0–100 — triggers PropertyChangeEvent
        );
    }

    @Override
    protected void process(List<String> chunks) {
        LOG.fine("Flushing " + chunks.size() + " log line(s) to UI");
        for (String msg : chunks) {
            if (logConsumer != null) logConsumer.accept(msg);
        }
    }

    @Override
    protected void done() {
        try {
            ImportResult result = get();
            LOG.info("Import task done: success=" + result.success()
                    + " logs=" + result.logs().size());
            if (result.success()) {
                if (logConsumer != null) logConsumer.accept("Done.");
            } else {
                if (logConsumer != null) logConsumer.accept("Finished with errors.");
                // Show an error popup with the last meaningful log line
                String errorDetail = lastError(result.logs());
                JOptionPane.showMessageDialog(
                        owner,
                        "Import failed:\n\n" + errorDetail,
                        "Import Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Import task threw an exception", e);
            String msg = "Task failed: " + e.getMessage();
            if (logConsumer != null) logConsumer.accept(msg);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            JTextArea textArea = new JTextArea(msg + "\n\n--- Stack Trace ---\n" + sw);
            textArea.setEditable(false);
            textArea.setCaretPosition(0);
            JScrollPane errScroll = new JScrollPane(textArea);
            errScroll.setPreferredSize(new Dimension(600, 300));
            JOptionPane.showMessageDialog(owner, errScroll, "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
