package org.example.core.service;

import org.example.core.model.AssetDiscoveryResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
     * Walks {@code folder} recursively and separates found files into MDX and texture lists.
     *
     * @param folder root directory to scan
     * @return discovery result with relative file paths and the root folder reference
     * @throws IOException if the directory cannot be walked
     */
    public AssetDiscoveryResult discover(File folder) throws IOException {
        LOG.info("Scanning folder: " + folder.getAbsolutePath());
        List<String> mdxFiles = new ArrayList<>();
        List<String> textureFiles = new ArrayList<>();

        Files.walk(folder.toPath())
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    String relPath = folder.toPath().relativize(path)
                            .toString()
                            .replace("\\", "/");
                    String lower = relPath.toLowerCase();
                    if (lower.endsWith(".mdx")) {
                        mdxFiles.add(relPath);
                    } else if (isTextureFile(lower)) {
                        textureFiles.add(relPath);
                    }
                });

        LOG.fine("Discovery complete: " + mdxFiles.size() + " MDX, " + textureFiles.size() + " textures in " + folder.getName());
        return new AssetDiscoveryResult(mdxFiles, textureFiles, folder);
    }
}
