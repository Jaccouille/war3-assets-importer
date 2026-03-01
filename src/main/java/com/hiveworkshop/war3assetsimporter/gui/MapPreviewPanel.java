package com.hiveworkshop.war3assetsimporter.gui;

import com.hiveworkshop.war3assetsimporter.core.model.ExistingUnit;
import com.hiveworkshop.war3assetsimporter.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Interactive map-preview panel used in the Import Configuration tab.
 *
 * <h3>Drawing</h3>
 * <ul>
 *   <li><b>Rectangle</b> – drag from any corner to the opposite corner.</li>
 *   <li><b>Circle</b>    – click the desired centre, then drag outward to set the radius.</li>
 * </ul>
 * All shape data is stored in <em>normalised image coordinates</em> [0..1] so that the
 * shape stays anchored to the map image when the panel is resized.
 *
 * <h3>Unit visualisation</h3>
 * Inside the drawn shape, a grid of units is placed with the configured spacing
 * (in screen pixels relative to the displayed image). Each unit is rendered as a
 * small triangle pointing in the configured facing direction, or as a coloured
 * square placeholder when icon mode is selected.
 *
 * <p>When {@code totalUnits > 0}, only that many positions are rendered (the rest
 * of the grid is still computed to report the full capacity via {@link CapacityListener}).
 */
public class MapPreviewPanel extends JPanel {

    // -------------------------------------------------------------------------
    // Enums & Listener
    // -------------------------------------------------------------------------

    private BufferedImage mapImage;
    private DrawingMode drawingMode = DrawingMode.RECTANGLE;
    // Shape endpoints in normalised image coordinates [0..1].
    // Rectangle: p1 = first corner, p2 = opposite corner.
    // Circle   : p1 = centre,       p2 = a point on the circumference.
    private double[] p1 = null;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private double[] p2 = null;
    // ---- Configuration (synced from ImportConfigPanel) ----
    private double unitAngle = 270.0;         // degrees, WC3 convention
    private double unitSpacingX = 16.0;          // screen pixels (scaled image)
    private double unitSpacingY = 16.0;
    private boolean showTriangles = true;
    private PlacingOrder placingOrder = PlacingOrder.ROWS;
    // ---- Unit count limiting ----
    private int totalUnits = 0;                        // 0 = show all grid positions
    // ---- Existing units from war3mapUnits.doo (shown as yellow triangles) ----
    private List<ExistingUnit> existingUnits = Collections.emptyList();
    // ---- Capacity callback ----
    private CapacityListener capacityListener;

    public MapPreviewPanel() {
        setBackground(new Color(35, 35, 35));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                p1 = toNorm(e.getPoint());
                p2 = p1.clone();
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                p2 = clamp(toNorm(e.getPoint()));
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                p2 = clamp(toNorm(e.getPoint()));
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    public void setMapImage(BufferedImage img) {
        mapImage = img;
        repaint();
    }

    public void setDrawingMode(DrawingMode m) {
        drawingMode = m;
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public void setUnitAngle(double deg) {
        unitAngle = deg;
        repaint();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setUnitSpacingX(double px) {
        unitSpacingX = px;
        repaint();
    }

    public void setUnitSpacingY(double px) {
        unitSpacingY = px;
        repaint();
    }

    public void setShowTriangles(boolean b) {
        showTriangles = b;
        repaint();
    }

    public void setPlacingOrder(PlacingOrder order) {
        placingOrder = order;
        repaint();
    }

    public void setTotalUnits(int count) {
        totalUnits = count;
        repaint();
    }

    public void setCapacityListener(CapacityListener l) {
        this.capacityListener = l;
    }

    /**
     * Replaces the existing-unit overlay (yellow triangles) with the supplied list.
     */
    public void setExistingUnits(List<ExistingUnit> units) {
        this.existingUnits = units != null ? units : Collections.emptyList();
        repaint();
    }

    public void clearShape() {
        p1 = null;
        p2 = null;
        fireCapacity(0, 0);
        repaint();
    }

    /**
     * Returns the drawn rectangle bounds in normalised image coordinates [0..1],
     * or {@code null} if no rectangle has been drawn yet.
     *
     * @return {@code double[4]} = {minX, minY, maxX, maxY} in [0..1], or {@code null}
     */
    public double[] getShapeBoundsNormalized() {
        if (p1 == null || p2 == null) return null;
        return new double[]{
                Math.min(p1[0], p2[0]),
                Math.min(p1[1], p2[1]),
                Math.max(p1[0], p2[0]),
                Math.max(p1[1], p2[1])
        };
    }

    /**
     * Returns the rectangle (in screen coords) where the map image is currently drawn,
     * or {@code null} if no map image is loaded.
     */
    public Rectangle getDisplayedImageRect() {
        if (mapImage == null) return null;
        return imageRect();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (mapImage == null) {
            paintNoImage(g);
            fireCapacity(0, 0);
            return;
        }

        Rectangle ir = imageRect();
        g.drawImage(mapImage, ir.x, ir.y, ir.width, ir.height, null);

        // Draw existing units from war3mapUnits.doo as yellow triangles
        for (ExistingUnit u : existingUnits) {
            int sx = (int) (u.normX() * ir.width + ir.x);
            int sy = (int) (u.normY() * ir.height + ir.y);
            paintExistingUnitTriangle(g, sx, sy, u.angleDeg());
        }

        if (p1 == null || p2 == null) {
            fireCapacity(0, 0);
            return;
        }

        Point sp1 = normToScreen(p1, ir);
        Point sp2 = normToScreen(p2, ir);

        paintRectangle(g, sp1, sp2);
    }

    private void paintNoImage(Graphics2D g) {
        g.setColor(new Color(55, 55, 55));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.GRAY);
        String msg = Messages.get("map.noMapLoaded");
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
    }

    private void paintRectangle(Graphics2D g, Point sp1, Point sp2) {
        int rx = Math.min(sp1.x, sp2.x);
        int ry = Math.min(sp1.y, sp2.y);
        int rw = Math.abs(sp2.x - sp1.x);
        int rh = Math.abs(sp2.y - sp1.y);
        if (rw < 1 || rh < 1) {
            fireCapacity(0, 0);
            return;
        }

        // Fill
        g.setColor(new Color(80, 140, 255, 45));
        g.fillRect(rx, ry, rw, rh);
        // Border
        g.setColor(new Color(100, 160, 255, 220));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(rx, ry, rw, rh);

        // Units
        List<Point> allPositions = rectGrid(rx, ry, rx + rw, ry + rh);
        paintUnitsWithLimit(g, allPositions);
    }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    /**
     * Renders unit positions (limited to {@code totalUnits} when &gt; 0) and
     * fires the {@link CapacityListener} with capacity information.
     */
    private void paintUnitsWithLimit(Graphics2D g, List<Point> allPositions) {
        int capacity = allPositions.size();
        int placed;

        if (totalUnits > 0) {
            placed = Math.min(totalUnits, capacity);
        } else {
            placed = capacity;
        }

        for (int i = 0; i < placed; i++) {
            paintUnit(g, allPositions.get(i));
        }

        fireCapacity(placed, capacity);
    }

    private void fireCapacity(int placed, int capacity) {
        if (capacityListener != null) {
            capacityListener.onCapacityChanged(placed, totalUnits, capacity);
        }
    }

    // ---- Rectangle ----

    private List<Point> rectGrid(int x1, int y1, int x2, int y2) {
        List<Point> out = new ArrayList<>();
        double sx = Math.max(1, unitSpacingX);
        double sy = Math.max(1, unitSpacingY);
        if (placingOrder == PlacingOrder.ROWS) {
            for (double y = y1 + sy / 2; y < y2; y += sy)
                for (double x = x1 + sx / 2; x < x2; x += sx)
                    out.add(new Point((int) x, (int) y));
        } else {
            for (double x = x1 + sx / 2; x < x2; x += sx)
                for (double y = y1 + sy / 2; y < y2; y += sy)
                    out.add(new Point((int) x, (int) y));
        }
        return out;
    }


    // -------------------------------------------------------------------------
    // Render units with capacity limiting
    // -------------------------------------------------------------------------

    private void paintUnit(Graphics2D g, Point p) {
        if (showTriangles) {
            paintTriangle(g, p.x, p.y);
        } else {
            // BLP-icon placeholder (gold square)
            g.setColor(new Color(255, 210, 0, 200));
            g.fillRect(p.x - 4, p.y - 4, 9, 9);
            g.setColor(new Color(200, 155, 0, 255));
            g.drawRect(p.x - 4, p.y - 4, 9, 9);
        }
    }

    // -------------------------------------------------------------------------
    // Capacity callback
    // -------------------------------------------------------------------------

    /**
     * Draws an arrow-head triangle at (x, y) pointing in the direction of {@link #unitAngle}.
     *
     * <p>WC3 angle convention: 0° = east, 90° = north.  Screen Y is inverted, so we
     * negate the sin component when converting to screen coordinates.
     */
    private void paintTriangle(Graphics2D g, int x, int y) {
        double rad = Math.toRadians(unitAngle);

        // Tip: forward — distance 14 px for good visibility at typical zoom levels
        int tipX = x + (int) (14 * Math.cos(rad));
        int tipY = y - (int) (14 * Math.sin(rad));   // negate for screen Y

        // Wings: 140° back from the forward direction (was 150°) → wider arrowhead
        double leftRad = rad + Math.toRadians(140);
        double rightRad = rad - Math.toRadians(140);
        int lx = x + (int) (9 * Math.cos(leftRad));
        int ly = y - (int) (9 * Math.sin(leftRad));
        int rx = x + (int) (9 * Math.cos(rightRad));
        int ry = y - (int) (9 * Math.sin(rightRad));

        int[] xs = {tipX, lx, rx};
        int[] ys = {tipY, ly, ry};

        g.setColor(new Color(255, 85, 85, 210));
        g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(180, 20, 20, 255));
        g.setStroke(new BasicStroke(0.8f));
        g.drawPolygon(xs, ys, 3);
    }

    // -------------------------------------------------------------------------
    // Unit position grids
    // -------------------------------------------------------------------------

    /**
     * Draws a yellow arrow-head triangle for a unit that already exists in the map,
     * using that unit's own facing angle rather than the global {@link #unitAngle}.
     */
    private void paintExistingUnitTriangle(Graphics2D g, int x, int y, double angleDeg) {
        double rad = Math.toRadians(angleDeg);

        int tipX = x + (int) (14 * Math.cos(rad));
        int tipY = y - (int) (14 * Math.sin(rad));   // negate for screen Y

        double leftRad = rad + Math.toRadians(140);
        double rightRad = rad - Math.toRadians(140);
        int lx = x + (int) (9 * Math.cos(leftRad));
        int ly = y - (int) (9 * Math.sin(leftRad));
        int rx = x + (int) (9 * Math.cos(rightRad));
        int ry = y - (int) (9 * Math.sin(rightRad));

        int[] xs = {tipX, lx, rx};
        int[] ys = {tipY, ly, ry};

        g.setColor(new Color(255, 220, 0, 200));
        g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(180, 140, 0, 255));
        g.setStroke(new BasicStroke(0.8f));
        g.drawPolygon(xs, ys, 3);
    }


    // -------------------------------------------------------------------------
    // Unit rendering
    // -------------------------------------------------------------------------

    /**
     * Returns the rectangle (in screen coords) where the map image is drawn.
     */
    private Rectangle imageRect() {
        if (mapImage == null) return new Rectangle(0, 0, getWidth(), getHeight());
        int pw = getWidth(), ph = getHeight();
        int iw = mapImage.getWidth(), ih = mapImage.getHeight();
        double scale = Math.min((double) pw / iw, (double) ph / ih);
        int sw = (int) (iw * scale), sh = (int) (ih * scale);
        return new Rectangle((pw - sw) / 2, (ph - sh) / 2, sw, sh);
    }

    /**
     * Screen point → normalised image coords [0..1].
     */
    private double[] toNorm(Point p) {
        Rectangle r = imageRect();
        if (r.width == 0 || r.height == 0) return new double[]{0, 0};
        return new double[]{
                (double) (p.x - r.x) / r.width,
                (double) (p.y - r.y) / r.height
        };
    }

    /**
     * Clamps normalised coords to [0..1].
     */
    private double[] clamp(double[] n) {
        return new double[]{
                Math.max(0, Math.min(1, n[0])),
                Math.max(0, Math.min(1, n[1]))
        };
    }

    // -------------------------------------------------------------------------
    // Coordinate helpers
    // -------------------------------------------------------------------------

    /**
     * Normalised image coords → screen point.
     */
    private Point normToScreen(double[] n, Rectangle r) {
        return new Point(
                (int) (n[0] * r.width + r.x),
                (int) (n[1] * r.height + r.y)
        );
    }

    public enum DrawingMode {
        RECTANGLE;
    }

    public enum PlacingOrder {
        ROWS, COLUMNS;

        @Override
        public String toString() {
            return Messages.get("placingOrder." + name().toLowerCase());
        }
    }

    /**
     * Callback fired after each paint cycle with updated placement counts.
     *
     * @see #setCapacityListener(CapacityListener)
     */
    public interface CapacityListener {
        /**
         * @param placed   number of unit positions actually rendered
         * @param total    number of MDX files selected (desired units)
         * @param capacity total grid slots available inside the shape
         */
        void onCapacityChanged(int placed, int total, int capacity);
    }
}
