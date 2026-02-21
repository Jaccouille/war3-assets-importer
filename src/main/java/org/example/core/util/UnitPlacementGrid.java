package org.example.core.util;

import net.moonlightflower.wc3libs.dataTypes.app.Coords2DF;

/**
 * Calculates sequential placement positions for units within map camera bounds.
 * Positions are arranged in a left-to-right, top-to-bottom grid with a fixed step size.
 */
public class UnitPlacementGrid {
    private final float startX;
    private final float startY;
    private final float endX;
    private final float endY;
    private final float step;

    private float currentX;
    private float currentY;
    private int unitsPlaced;

    public UnitPlacementGrid(Coords2DF topLeft, Coords2DF bottomRight, float step) {
        this.startX = topLeft.getX().getVal();
        this.startY = topLeft.getY().getVal();
        this.endX = bottomRight.getX().getVal();
        this.endY = bottomRight.getY().getVal();
        this.step = step;

        this.currentX = startX;
        this.currentY = startY;
        this.unitsPlaced = 0;
    }

    /**
     * Returns the next grid position, or {@code null} if the grid is exhausted.
     */
    public Coords2DF nextPosition() {
        if (currentY < endY) {
            // No more space to place units
            return null;
        }

        Coords2DF pos = new Coords2DF(currentX, currentY);
        currentX += step;
        if (currentX > endX) {
            currentX = startX;
            currentY -= step;
        }

        unitsPlaced++;
        return pos;
    }

    public int getUnitsPlaced() {
        return unitsPlaced;
    }
}
