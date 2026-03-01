package org.example.core.util;

import net.moonlightflower.wc3libs.dataTypes.app.Coords2DF;

/**
 * Singleton holding the camera boundary coordinates extracted from a loaded map.
 * Consumed by UnitPlacementGrid and ImportService for placing units within valid map bounds.
 */
public class CameraBounds {
    private static final CameraBounds instance = new CameraBounds();

    private Coords2DF topLeft;
    private Coords2DF topRight;
    private Coords2DF bottomRight;
    private Coords2DF bottomLeft;

    private CameraBounds() {
        // private constructor
    }

    public static CameraBounds getInstance() {
        return instance;
    }

    public void setCameraBounds(Coords2DF bottomLeft, Coords2DF topRight, Coords2DF topLeft, Coords2DF bottomRight) {
        this.topLeft = topLeft;
        this.topRight = topRight;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
    }

    public Coords2DF getTopLeft() {
        return topLeft;
    }

    public Coords2DF getTopRight() {
        return topRight;
    }

    public Coords2DF getBottomRight() {
        return bottomRight;
    }

    public Coords2DF getBottomLeft() {
        return bottomLeft;
    }

    public boolean isInitialized() {
        return topLeft != null && bottomRight != null && topRight != null && bottomLeft != null;
    }
}
