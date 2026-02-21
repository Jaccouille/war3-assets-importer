package org.example.core.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssetDiscoveryResult {
    private final List<String> mdxFiles;
    private final List<String> blpFiles;
    private final File rootFolder;

    public AssetDiscoveryResult(List<String> mdxFiles, List<String> blpFiles, File rootFolder) {
        this.mdxFiles = Collections.unmodifiableList(new ArrayList<>(mdxFiles));
        this.blpFiles = Collections.unmodifiableList(new ArrayList<>(blpFiles));
        this.rootFolder = rootFolder;
    }

    public List<String> mdxFiles()  { return mdxFiles; }
    public List<String> blpFiles()  { return blpFiles; }
    public File rootFolder()        { return rootFolder; }

    public int totalFileCount() {
        return mdxFiles.size() + blpFiles.size();
    }
}