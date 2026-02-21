package org.example.core.service;

import org.example.core.model.AssetDiscoveryResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans a directory tree for MDX (model) and BLP (texture) asset files.
 * Used by both the GUI (after the user picks a folder) and the CLI (via --folder flag).
 */
public class AssetDiscoveryService {

    /**
     * Walks {@code folder} recursively and separates found files into MDX and BLP lists.
     *
     * @param folder root directory to scan
     * @return discovery result with relative file paths and the root folder reference
     * @throws IOException if the directory cannot be walked
     */
    public AssetDiscoveryResult discover(File folder) throws IOException {
        List<String> mdxFiles = new ArrayList<>();
        List<String> blpFiles = new ArrayList<>();

        Files.walk(folder.toPath())
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    String relPath = folder.toPath().relativize(path)
                            .toString()
                            .replace("\\", "/");
                    String lower = relPath.toLowerCase();
                    if (lower.endsWith(".mdx")) {
                        mdxFiles.add(relPath);
                    } else if (lower.endsWith(".blp")) {
                        blpFiles.add(relPath);
                    }
                });

        return new AssetDiscoveryResult(mdxFiles, blpFiles, folder);
    }
}
