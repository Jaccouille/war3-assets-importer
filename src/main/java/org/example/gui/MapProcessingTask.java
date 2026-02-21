package org.example.gui;

import org.example.core.model.ImportOptions;
import org.example.core.model.ImportResult;
import org.example.core.service.ImportService;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * SwingWorker that runs {@link ImportService#process} on a background thread
 * and forwards progress log lines to the EDT via {@link #process(List)}.
 */
public class MapProcessingTask extends SwingWorker<ImportResult, String> {

    private final File mapFile;
    private final Set<Path> selectedFiles;
    private final File assetsRootFolder;
    private final ImportOptions options;
    private final ImportService importService;
    private final Consumer<String> logConsumer;

    public MapProcessingTask(
            File mapFile,
            Set<Path> selectedFiles,
            File assetsRootFolder,
            ImportOptions options,
            ImportService importService,
            Consumer<String> logConsumer
    ) {
        this.mapFile = mapFile;
        this.selectedFiles = selectedFiles;
        this.assetsRootFolder = assetsRootFolder;
        this.options = options;
        this.importService = importService;
        this.logConsumer = logConsumer;
    }

    @Override
    protected ImportResult doInBackground() {
        return importService.process(
                mapFile,
                selectedFiles,
                assetsRootFolder,
                options,
                msg -> publish(msg)          // thread-safe: routes through process() below
        );
    }

    @Override
    protected void process(List<String> chunks) {
        // Back on the EDT — forward each line to the UI log consumer
        for (String msg : chunks) {
            if (logConsumer != null) logConsumer.accept(msg);
        }
    }

    @Override
    protected void done() {
        try {
            ImportResult result = get();
            if (logConsumer != null) {
                logConsumer.accept(result.success()
                        ? "Done."
                        : "Finished with errors.");
            }
        } catch (Exception e) {
            if (logConsumer != null) logConsumer.accept("Task failed: " + e.getMessage());
        }
    }
}
