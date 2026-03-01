package com.hiveworkshop.war3assetsimporter.core.model;

public final class ImportOptions {

    private final boolean createUnits;
    private final boolean placeUnits;
    private final boolean clearUnits;
    private final boolean clearAssets;
    private final double unitScaling;
    private final float unitAngle;
    private final double unitSpacingX;
    private final double unitSpacingY;
    private final String unitOriginId;
    private final boolean autoNameUnits;
    private final String nameFormat;
    private final boolean autoAssignIcon;
    /**
     * When true, assets are stored in the MPQ under their filename only (no subfolder path).
     */
    private final boolean flattenPaths;
    private final PlacingOrder placingOrder;
    /**
     * Optional drawn-shape bounds in world space; {@code null} = fall back to camera bounds.
     */
    private final PlacementBounds placementBounds;
    public ImportOptions(boolean createUnits, boolean placeUnits, boolean clearUnits,
                         boolean clearAssets, String unitOriginId) {
        this(createUnits, placeUnits, clearUnits, clearAssets, 1.0, 270, 64.0, 64.0, unitOriginId, false, "Space Separated", false, false, PlacingOrder.ROWS, null);
    }
    public ImportOptions(boolean createUnits, boolean placeUnits, boolean clearUnits,
                         boolean clearAssets, double unitScaling,
                         float unitAngle, double unitSpacingX, double unitSpacingY,
                         String unitOriginId,
                         boolean autoNameUnits, String nameFormat,
                         boolean autoAssignIcon, boolean flattenPaths,
                         PlacingOrder placingOrder,
                         PlacementBounds placementBounds) {
        this.createUnits = createUnits;
        this.placeUnits = placeUnits;
        this.clearUnits = clearUnits;
        this.clearAssets = clearAssets;
        this.unitScaling = unitScaling;
        this.unitAngle = unitAngle;
        this.unitSpacingX = unitSpacingX;
        this.unitSpacingY = unitSpacingY;
        this.unitOriginId = unitOriginId;
        this.autoNameUnits = autoNameUnits;
        this.nameFormat = nameFormat;
        this.autoAssignIcon = autoAssignIcon;
        this.flattenPaths = flattenPaths;
        this.placingOrder = placingOrder;
        this.placementBounds = placementBounds;
    }

    public static ImportOptions defaults() {
        return new ImportOptions(true, true, false, false, "hfoo");
    }

    public float getUnitAngle() {
        return unitAngle;
    }

    public boolean getCreateUnits() {
        return createUnits;
    }

    public boolean getPlaceUnits() {
        return placeUnits;
    }

    public boolean getClearUnits() {
        return clearUnits;
    }

    public boolean getClearAssets() {
        return clearAssets;
    }

    public double getUnitScaling() {
        return unitScaling;
    }

    public double getUnitSpacingX() {
        return unitSpacingX;
    }

    public double getUnitSpacingY() {
        return unitSpacingY;
    }

    public String getUnitOriginId() {
        return unitOriginId;
    }

    public boolean getAutoNameUnits() {
        return autoNameUnits;
    }

    public String getNameFormat() {
        return nameFormat;
    }

    public boolean getAutoAssignIcon() {
        return autoAssignIcon;
    }

    public boolean getFlattenPaths() {
        return flattenPaths;
    }

    public PlacingOrder getPlacingOrder() {
        return placingOrder;
    }

    /**
     * Returns the world-space placement bounds derived from a drawn shape, or
     * {@code null} if no shape was drawn (ImportService will fall back to camera bounds).
     */
    public PlacementBounds getPlacementBounds() {
        return placementBounds;
    }

    public enum PlacingOrder {ROWS, COLUMNS}

    /**
     * World-space placement region derived from a shape drawn on the map preview.
     *
     * <p>All coordinates are in WC3 world units (same system as DOO_UNITS positions).
     * {@code minY} is the southern edge (lower value), {@code maxY} is the northern edge.
     * {@code spacingX/Y} are already in world units (converted from screen pixels).
     */
    public record PlacementBounds(
            float minX, float minY,
            float maxX, float maxY,
            float spacingX, float spacingY
    ) {
    }
}
