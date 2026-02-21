package org.example.gui;

public final class TreeNodeData {
    private final String name;
    private final boolean isFile;
    private final String relativePath;
    private final long sizeInBytes;
    private final int fileCount;

    public TreeNodeData(String name, boolean isFile, String relativePath, long sizeInBytes, int fileCount) {
        this.name = name;
        this.isFile = isFile;
        this.relativePath = relativePath;
        this.sizeInBytes = sizeInBytes;
        this.fileCount = fileCount;
    }

    public String name()         { return name; }
    public boolean isFile()      { return isFile; }
    public String relativePath() { return relativePath; }
    public long sizeInBytes()    { return sizeInBytes; }
    public int fileCount()       { return fileCount; }

    @Override
    public String toString() {
        String sizeStr = formatSize(sizeInBytes);
        if (isFile) {
            return name + " [" + sizeStr + "]";
        } else {
            return name + " (" + fileCount + " files, " + sizeStr + ")";
        }
    }

    private String formatSize(long size) {
        if (size >= 1 << 20) return String.format("%.1f MB", size / 1024.0 / 1024);
        if (size >= 1 << 10) return String.format("%.1f KB", size / 1024.0);
        return size + " B";
    }
}