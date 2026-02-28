package org.example.core.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssetDiscoveryResult {
    private final List<String> mdxFiles;
    private final List<String> textureFiles;
    private final File rootFolder;

    public AssetDiscoveryResult(List<String> mdxFiles, List<String> textureFiles, File rootFolder) {
        this.mdxFiles = Collections.unmodifiableList(new ArrayList<>(mdxFiles));
        this.textureFiles = Collections.unmodifiableList(new ArrayList<>(textureFiles));
        this.rootFolder = rootFolder;
    }

    public List<String> mdxFiles()      { return mdxFiles; }
    public List<String> textureFiles()  { return textureFiles; }
    public File rootFolder()            { return rootFolder; }

    public int totalFileCount() {
        return mdxFiles.size() + textureFiles.size();
    }
}