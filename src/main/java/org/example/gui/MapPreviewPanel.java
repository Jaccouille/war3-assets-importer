package org.example.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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
 */
public class MapPreviewPanel extends JPanel {

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------

    public enum DrawingMode {
        RECTANGLE, CIRCLE;
    }

    public enum PlacingOrder {
        ROWS("Rows"), COLUMNS("Columns");

        private final String label;
        PlacingOrder(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private BufferedImage mapImage;
    private DrawingMode   drawingMode  = DrawingMode.RECTANGLE;

    // Shape endpoints in normalised image coordinates [0..1].
    // Rectangle: p1 = first corner, p2 = opposite corner.
    // Circle   : p1 = centre,       p2 = a point on the circumference.
    private double[] p1 = null;
    private double[] p2 = null;

    // ---- Configuration (synced from ImportConfigPanel) ----
    private double       unitAngle    = 270.0;         // degrees, WC3 convention
    private double       unitSpacing  = 64.0;          // screen pixels (scaled image)
    private boolean      showTriangles = true;
    private PlacingOrder placingOrder  = PlacingOrder.ROWS;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public MapPreviewPanel() {
        setBackground(new Color(35, 35, 35));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                p1 = toNorm(e.getPoint());
                p2 = p1.clone();
                repaint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                p2 = clamp(toNorm(e.getPoint()));
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                p2 = clamp(toNorm(e.getPoint()));
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setMapImage(BufferedImage img)      { mapImage = img;         repaint(); }
    public void setDrawingMode(DrawingMode m)        { drawingMode = m; }
    public void setUnitAngle(double deg)             { unitAngle = deg;        repaint(); }
    public void setUnitSpacing(double px)            { unitSpacing = px;       repaint(); }
    public void setShowTriangles(boolean b)          { showTriangles = b;      repaint(); }
    public void setPlacingOrder(PlacingOrder order)  { placingOrder = order;   repaint(); }
    public void clearShape()                         { p1 = null; p2 = null;  repaint(); }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,       RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (mapImage == null) {
            paintNoImage(g);
            return;
        }

        Rectangle ir = imageRect();
        g.drawImage(mapImage, ir.x, ir.y, ir.width, ir.height, null);

        if (p1 == null || p2 == null) return;

        Point sp1 = normToScreen(p1, ir);
        Point sp2 = normToScreen(p2, ir);

        if (drawingMode == DrawingMode.RECTANGLE) {
            paintRectangle(g, sp1, sp2);
        } else {
            paintCircle(g, sp1, sp2);
        }
    }

    private void paintNoImage(Graphics2D g) {
        g.setColor(new Color(55, 55, 55));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.GRAY);
        String msg = "Open a map to display the preview here";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
    }

    // ---- Rectangle ----

    private void paintRectangle(Graphics2D g, Point sp1, Point sp2) {
        int rx = Math.min(sp1.x, sp2.x);
        int ry = Math.min(sp1.y, sp2.y);
        int rw = Math.abs(sp2.x - sp1.x);
        int rh = Math.abs(sp2.y - sp1.y);
        if (rw < 1 || rh < 1) return;

        // Fill
        g.setColor(new Color(80, 140, 255, 45));
        g.fillRect(rx, ry, rw, rh);
        // Border
        g.setColor(new Color(100, 160, 255, 220));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(rx, ry, rw, rh);

        // Units
        for (Point u : rectGrid(rx, ry, rx + rw, ry + rh)) paintUnit(g, u);
    }

    // ---- Circle ----

    private void paintCircle(Graphics2D g, Point centre, Point edge) {
        int r = (int) Math.round(centre.distance(edge));
        if (r < 1) return;

        // Fill
        g.setColor(new Color(80, 220, 130, 45));
        g.fillOval(centre.x - r, centre.y - r, 2 * r, 2 * r);
        // Border
        g.setColor(new Color(100, 230, 150, 220));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(centre.x - r, centre.y - r, 2 * r, 2 * r);
        // Radius handle
        g.setColor(new Color(100, 230, 150, 180));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{4f, 4f}, 0f));
        g.drawLine(centre.x, centre.y, edge.x, edge.y);

        // Units
        for (Point u : circleGrid(centre.x, centre.y, r)) paintUnit(g, u);
    }

    // -------------------------------------------------------------------------
    // Unit position grids
    // -------------------------------------------------------------------------

    private List<Point> rectGrid(int x1, int y1, int x2, int y2) {
        List<Point> out = new ArrayList<>();
        double s = Math.max(1, unitSpacing);
        if (placingOrder == PlacingOrder.ROWS) {
            for (double y = y1 + s / 2; y < y2; y += s)
                for (double x = x1 + s / 2; x < x2; x += s)
                    out.add(new Point((int) x, (int) y));
        } else {
            for (double x = x1 + s / 2; x < x2; x += s)
                for (double y = y1 + s / 2; y < y2; y += s)
                    out.add(new Point((int) x, (int) y));
        }
        return out;
    }

    private List<Point> circleGrid(int cx, int cy, int r) {
        List<Point> out = new ArrayList<>();
        double s  = Math.max(1, unitSpacing);
        double r2 = (double) r * r;
        if (placingOrder == PlacingOrder.ROWS) {
            for (double y = cy - r + s / 2; y < cy + r; y += s)
                for (double x = cx - r + s / 2; x < cx + r; x += s)
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r2)
                        out.add(new Point((int) x, (int) y));
        } else {
            for (double x = cx - r + s / 2; x < cx + r; x += s)
                for (double y = cy - r + s / 2; y < cy + r; y += s)
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r2)
                        out.add(new Point((int) x, (int) y));
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Unit rendering
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

    /**
     * Draws an arrow-head triangle at (x, y) pointing in the direction of {@link #unitAngle}.
     *
     * <p>WC3 angle convention: 0° = east, 90° = north.  Screen Y is inverted, so we
     * negate the sin component when converting to screen coordinates.
     */
    private void paintTriangle(Graphics2D g, int x, int y) {
        double rad = Math.toRadians(unitAngle);

        // Tip: forward
        int tipX = x + (int) (9 * Math.cos(rad));
        int tipY = y - (int) (9 * Math.sin(rad));   // negate for screen Y

        // Wings: 150° back-left and back-right from the forward direction
        double leftRad  = rad + Math.toRadians(150);
        double rightRad = rad - Math.toRadians(150);
        int lx = x + (int) (6 * Math.cos(leftRad));
        int ly = y - (int) (6 * Math.sin(leftRad));
        int rx = x + (int) (6 * Math.cos(rightRad));
        int ry = y - (int) (6 * Math.sin(rightRad));

        int[] xs = {tipX, lx, rx};
        int[] ys = {tipY, ly, ry};

        g.setColor(new Color(255, 85, 85, 210));
        g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(180, 20, 20, 255));
        g.setStroke(new BasicStroke(0.8f));
        g.drawPolygon(xs, ys, 3);
    }

    // -------------------------------------------------------------------------
    // Coordinate helpers
    // -------------------------------------------------------------------------

    /** Returns the rectangle (in screen coords) where the map image is drawn. */
    private Rectangle imageRect() {
        if (mapImage == null) return new Rectangle(0, 0, getWidth(), getHeight());
        int pw = getWidth(),  ph = getHeight();
        int iw = mapImage.getWidth(), ih = mapImage.getHeight();
        double scale = Math.min((double) pw / iw, (double) ph / ih);
        int sw = (int) (iw * scale), sh = (int) (ih * scale);
        return new Rectangle((pw - sw) / 2, (ph - sh) / 2, sw, sh);
    }

    /** Screen point → normalised image coords [0..1]. */
    private double[] toNorm(Point p) {
        Rectangle r = imageRect();
        if (r.width == 0 || r.height == 0) return new double[]{0, 0};
        return new double[]{
                (double) (p.x - r.x) / r.width,
                (double) (p.y - r.y) / r.height
        };
    }

    /** Clamps normalised coords to [0..1]. */
    private double[] clamp(double[] n) {
        return new double[]{
                Math.max(0, Math.min(1, n[0])),
                Math.max(0, Math.min(1, n[1]))
        };
    }

    /** Normalised image coords → screen point. */
    private Point normToScreen(double[] n, Rectangle r) {
        return new Point(
                (int) (n[0] * r.width  + r.x),
                (int) (n[1] * r.height + r.y)
        );
    }
}
