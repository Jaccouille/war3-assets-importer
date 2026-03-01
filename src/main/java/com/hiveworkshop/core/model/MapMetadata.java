package org.example.core.model;

import org.example.core.util.CameraBounds;

public final class MapMetadata {
    private final String name;
    private final String author;
    private final String gameVersion;
    private final String editorVersion;
    private final String description;
    private final byte[] previewImageBytes;
    private final CameraBounds cameraBounds;

    /**
     * Non-null when metadata could only be partially loaded (e.g. unsupported W3I format).
     * Callers should log this as a warning rather than treating it as a fatal error.
     */
    private final String loadWarning;

    /** Full-metadata constructor (no warning). */
    public MapMetadata(String name, String author, String gameVersion, String editorVersion,
                       String description, byte[] previewImageBytes, CameraBounds cameraBounds) {
        this(name, author, gameVersion, editorVersion, description, previewImageBytes, cameraBounds, null);
    }

    /** Partial-metadata constructor: supply a non-null {@code loadWarning} when parsing was incomplete. */
    public MapMetadata(String name, String author, String gameVersion, String editorVersion,
                       String description, byte[] previewImageBytes, CameraBounds cameraBounds,
                       String loadWarning) {
        this.name = name;
        this.author = author;
        this.gameVersion = gameVersion;
        this.editorVersion = editorVersion;
        this.description = description;
        this.previewImageBytes = previewImageBytes;
        this.cameraBounds = cameraBounds;
        this.loadWarning = loadWarning;
    }

    public String name()               { return name; }
    public String author()             { return author; }
    public String gameVersion()        { return gameVersion; }
    public String editorVersion()      { return editorVersion; }
    public String description()        { return description; }
    public byte[] previewImageBytes()  { return previewImageBytes; }
    public CameraBounds cameraBounds() { return cameraBounds; }

    /**
     * Returns a warning message when metadata loading was only partial, or
     * {@code null} when the map was fully parsed.
     */
    public String loadWarning()        { return loadWarning; }
}