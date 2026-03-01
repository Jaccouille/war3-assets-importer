package com.hiveworkshop.war3assetsimporter.core.model;

import java.io.File;
import java.util.*;

public final class AssetDiscoveryResult {
    private final List<String> mdxFiles;
    private final List<String> textureFiles;
    private final File rootFolder;
    private final Map<String, Long> fileSizes;

    public AssetDiscoveryResult(List<String> mdxFiles, List<String> textureFiles,
                                File rootFolder, Map<String, Long> fileSizes) {
        this.mdxFiles = Collections.unmodifiableList(new ArrayList<>(mdxFiles));
        this.textureFiles = Collections.unmodifiableList(new ArrayList<>(textureFiles));
        this.rootFolder = rootFolder;
        this.fileSizes = Collections.unmodifiableMap(new HashMap<>(fileSizes));
    }

    /**
     * Backward-compatible constructor (CLI path — sizes not needed).
     */
    public AssetDiscoveryResult(List<String> mdxFiles, List<String> textureFiles, File rootFolder) {
        this(mdxFiles, textureFiles, rootFolder, Collections.emptyMap());
    }

    public List<String> mdxFiles() {
        return mdxFiles;
    }

    public List<String> textureFiles() {
        return textureFiles;
    }

    public File rootFolder() {
        return rootFolder;
    }

    public Map<String, Long> fileSizes() {
        return fileSizes;
    }

    public int totalFileCount() {
        return mdxFiles.size() + textureFiles.size();
    }
}