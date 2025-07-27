package org.example;

public record TreeNodeData(String name, boolean isFile, String relativePath, long sizeInBytes, int fileCount) {

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

