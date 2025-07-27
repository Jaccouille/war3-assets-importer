package org.example;

import net.moonlightflower.wc3libs.dataTypes.app.Coords2DF;

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
