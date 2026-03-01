package com.hiveworkshop.war3assetsimporter.core.util;

import com.hiveworkshop.war3assetsimporter.core.model.ImportOptions;
import net.moonlightflower.wc3libs.dataTypes.app.Coords2DF;

import java.util.logging.Logger;

/**
 * Calculates sequential placement positions for units within map camera bounds.
 * Positions are arranged in a left-to-right, top-to-bottom grid with configurable
 * horizontal and vertical step sizes.
 */
public class UnitPlacementGrid {

    private static final Logger LOG = Logger.getLogger(UnitPlacementGrid.class.getName());

    private final float startX;
    private final float startY;
    private final float endX;
    private final float endY;
    private final float stepX;
    private final float stepY;
    private final boolean columnsFirst;
    private float currentX;
    private float currentY;
    private int unitsPlaced;

    public UnitPlacementGrid(Coords2DF topLeft, Coords2DF bottomRight, float stepX, float stepY) {
        this(topLeft.getX().getVal(),
                bottomRight.getX().getVal(),
                bottomRight.getY().getVal(),
                topLeft.getY().getVal(),
                stepX, stepY, ImportOptions.PlacingOrder.ROWS);
    }

    public UnitPlacementGrid(Coords2DF topLeft, Coords2DF bottomRight, float stepX, float stepY,
                             ImportOptions.PlacingOrder order) {
        this(topLeft.getX().getVal(),
                bottomRight.getX().getVal(),
                bottomRight.getY().getVal(),
                topLeft.getY().getVal(),
                stepX, stepY, order);
    }

    /**
     * Creates a grid from explicit world-space min/max bounds.
     *
     * @param minX  western boundary (smallest X)
     * @param maxX  eastern boundary (largest X)
     * @param minY  southern boundary (smallest Y in WC3 world coords)
     * @param maxY  northern boundary (largest Y in WC3 world coords)
     * @param stepX horizontal spacing between units (world units)
     * @param stepY vertical spacing between units (world units)
     * @param order whether to fill row-by-row or column-by-column
     */
    public UnitPlacementGrid(float minX, float maxX, float minY, float maxY,
                             float stepX, float stepY, ImportOptions.PlacingOrder order) {
        this.startX = minX;
        this.startY = maxY;  // start from the north (high Y)
        this.endX = maxX;
        this.endY = minY;  // stop at the south (low Y)
        this.stepX = stepX;
        this.stepY = stepY;
        this.columnsFirst = (order == ImportOptions.PlacingOrder.COLUMNS);

        this.currentX = startX;
        this.currentY = startY;
        this.unitsPlaced = 0;

        LOG.fine(String.format(
                "UnitPlacementGrid created: bounds=(%.0f,%.0f)→(%.0f,%.0f) step=(%.1f,%.1f) order=%s capacity≈%d",
                startX, startY, endX, endY, stepX, stepY, order,
                (int) ((endX - startX) / stepX + 1) * (int) ((startY - endY) / stepY + 1)));
    }

    /**
     * Returns the next grid position, or {@code null} if the grid is exhausted.
     */
    public Coords2DF nextPosition() {
        if (columnsFirst) {
            if (currentX > endX) {
                LOG.fine("UnitPlacementGrid exhausted after " + unitsPlaced + " unit(s)");
                return null;
            }
            Coords2DF pos = new Coords2DF(currentX, currentY);
            LOG.finest(String.format("nextPosition: placing unit %d at (%.1f, %.1f)", unitsPlaced + 1, currentX, currentY));
            currentY -= stepY;
            if (currentY < endY) {
                currentY = startY;
                currentX += stepX;
            }
            unitsPlaced++;
            return pos;
        } else {
            if (currentY < endY) {
                LOG.fine("UnitPlacementGrid exhausted after " + unitsPlaced + " unit(s)");
                return null;
            }
            Coords2DF pos = new Coords2DF(currentX, currentY);
            LOG.finest(String.format("nextPosition: placing unit %d at (%.1f, %.1f)", unitsPlaced + 1, currentX, currentY));
            currentX += stepX;
            if (currentX > endX) {
                currentX = startX;
                currentY -= stepY;
            }
            unitsPlaced++;
            return pos;
        }
    }

    public int getUnitsPlaced() {
        LOG.fine("getUnitsPlaced: " + unitsPlaced);
        return unitsPlaced;
    }
}
