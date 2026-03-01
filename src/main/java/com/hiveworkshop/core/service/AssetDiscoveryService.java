package org.example.core.service;

import org.example.core.model.AssetDiscoveryResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * Scans a directory tree for MDX (model) and texture asset files.
 * Recognised texture formats: BLP, DDS, TGA, PNG, JPG/JPEG, BMP, GIF.
 * Used by both the GUI (after the user picks a folder) and the CLI (via --folder flag).
 */
public class AssetDiscoveryService {

    private static final Logger LOG = Logger.getLogger(AssetDiscoveryService.class.getName());

    private static final java.util.Set<String> TEXTURE_EXTENSIONS = new java.util.HashSet<>(
            java.util.Arrays.asList(".blp", ".dds", ".tga", ".png", ".jpg", ".jpeg", ".bmp", ".gif"));

    private static boolean isTextureFile(String lowerPath) {
        int dot = lowerPath.lastIndexOf('.');
        return dot >= 0 && TEXTURE_EXTENSIONS.contains(lowerPath.substring(dot));
    }

    /**
     * Walks {@code folder} recursively, collecting MDX/texture paths and file sizes.
     * Supports cooperative cancellation: the walk stops early when {@code cancelled} returns true.
     *
     * @param folder    root directory to scan
     * @param cancelled checked before each file; return {@code true} to abort
     * @return discovery result, or a partial result if cancelled
     * @throws IOException if the directory cannot be walked
     */
    public AssetDiscoveryResult discover(File folder, BooleanSupplier cancelled) throws IOException {
        LOG.info("Scanning folder: " + folder.getAbsolutePath());
        List<String>      mdxFiles     = new ArrayList<>();
        List<String>      textureFiles = new ArrayList<>();
        Map<String, Long> fileSizes    = new HashMap<>();
        java.nio.file.Path root = folder.toPath();

        Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    if (cancelled.getAsBoolean()) return;
                    String relPath = root.relativize(path).toString().replace("\\", "/");
                    long size = 0;
                    try { size = Files.size(path); } catch (IOException ignored) {}
                    fileSizes.put(relPath, size);
                    String lower = relPath.toLowerCase();
                    if (lower.endsWith(".mdx")) {
                        mdxFiles.add(relPath);
                    } else if (isTextureFile(lower)) {
                        textureFiles.add(relPath);
                    }
                });

        LOG.fine("Discovery complete: " + mdxFiles.size() + " MDX, " + textureFiles.size()
                + " textures in " + folder.getName());
        return new AssetDiscoveryResult(mdxFiles, textureFiles, folder, fileSizes);
    }

    /** Convenience overload for the CLI (no cancellation needed). */
    public AssetDiscoveryResult discover(File folder) throws IOException {
        return discover(folder, () -> false);
    }
}
