package org.example.gui;

import org.example.gui.i18n.Messages;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

/**
 * Panel that displays either:
 * <ul>
 *   <li>A single scaled preview of a selected asset (BLP texture or placeholder for MDX)</li>
 *   <li>A scrollable thumbnail grid when a folder is focused in the asset tree</li>
 * </ul>
 *
 * <p>Grid thumbnails are loaded asynchronously via a {@link SwingWorker} so the EDT
 * is never blocked.  Switching view (single ↔ grid) cancels any in-flight load.
 *
 * <h3>Why the grid wraps correctly</h3>
 * <p>{@link FlowLayout#preferredLayoutSize} always returns the single-row size — it
 * never accounts for wrapping.  If we rely on it, the {@link JScrollPane} is told the
 * content is only one row tall and never enables vertical scrolling.
 * <p>The fix is {@link ScrollableFlowPanel}:
 * <ol>
 *   <li>{@code getScrollableTracksViewportWidth() = true} tells the scroll pane to
 *       force the panel's width to the viewport width, so {@link FlowLayout#layoutContainer}
 *       (which uses the component's actual width) wraps items visually.</li>
 *   <li>A custom {@code getPreferredSize()} simulates the same wrapping arithmetic and
 *       returns the correct multi-row height, so the scroll pane knows content is taller
 *       than the viewport and activates the vertical scrollbar.</li>
 * </ol>
 */
public class PreviewPanel extends JPanel {

    private static final int PREVIEW_SIZE = 256;

    /** Available thumbnail sizes, similar to Windows Explorer view options. */
    public enum IconSize {
        EXTRA_LARGE("Extra Large Icons", 256),
        LARGE("Large Icons",             128),
        MEDIUM("Medium Icons",            96),
        SMALL("Small Icons",              48);

        private final String label;
        private final int pixels;

        IconSize(String label, int pixels) {
            this.label = label;
            this.pixels = pixels;
        }

        public int getPixels() { return pixels; }

        @Override public String toString() { return label; }
    }

    // ---- Current thumbnail size (user-selectable) ----
    private IconSize currentIconSize = IconSize.MEDIUM;
    private int thumbSize = IconSize.MEDIUM.getPixels();

    // ---- Single-image mode ----
    private final JLabel singleLabel     = new JLabel();
    private final JPanel singleContainer;

    // ---- Grid mode ----
    // Non-static inner class so it can read scrollPane.getViewport().getWidth()
    private final ScrollableFlowPanel gridPanel = new ScrollableFlowPanel();

    // ---- Shared scroll pane (always present) ----
    private final JScrollPane scrollPane;

    // ---- Async thumb loading ----
    private SwingWorker<Void, ThumbResult> thumbLoader;

    // ---- Viewport-bounded thumbnail state ----
    private List<File>   currentFiles  = List.of();
    private List<JLabel> currentLabels = List.of();
    private final BitSet decoded = new BitSet(); // true = label has a real image
    private javax.swing.event.ChangeListener viewportListener;
    /** Extra rows to load beyond the visible edge in each direction. */
    private static final int OVERSCAN_ROWS = 2;
    private boolean inGridMode = false;

    // ---- Size selector ----
    private final JComboBox<IconSize> sizeCombo;

    public PreviewPanel() {
        setLayout(new BorderLayout());

        // ---- Icon size toolbar ----
        sizeCombo = new JComboBox<>(IconSize.values());
        sizeCombo.setSelectedItem(IconSize.MEDIUM);
        sizeCombo.setMaximumSize(sizeCombo.getPreferredSize());
        sizeCombo.addActionListener(e -> onIconSizeChanged());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(new JLabel(" View: "));
        toolbar.add(sizeCombo);

        add(toolbar, BorderLayout.NORTH);

        // Single-image: centred label inside a GridBagLayout panel
        singleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        singleLabel.setVerticalAlignment(SwingConstants.CENTER);
        singleContainer = new JPanel(new GridBagLayout());
        singleContainer.add(singleLabel);

        // Start in single-image mode
        scrollPane = new JScrollPane(singleContainer);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        applyI18n();
        setPreferredSize(new Dimension(PREVIEW_SIZE + 40, PREVIEW_SIZE + 40));
    }

    private void onIconSizeChanged() {
        IconSize selected = (IconSize) sizeCombo.getSelectedItem();
        if (selected == null || selected == currentIconSize) return;
        currentIconSize = selected;
        thumbSize = selected.getPixels();

        // Re-render current view with new size
        if (inGridMode && !currentFiles.isEmpty()) {
            setImages(currentFiles);
        }
    }

    // -------------------------------------------------------------------------
    // Single-image setters
    // -------------------------------------------------------------------------

    public void setImage(BufferedImage image) {
        cancelThumbLoader();
        inGridMode = false;
        switchTo(singleContainer);
        singleLabel.setIcon(image != null
                ? scaled(image, PREVIEW_SIZE)
                : fallback(PREVIEW_SIZE, 4));
    }

    public void setImage(File file) {
        cancelThumbLoader();
        inGridMode = false;
        switchTo(singleContainer);
        try {
            BufferedImage img = ImageIO.read(file);
            singleLabel.setIcon(img != null ? scaled(img, PREVIEW_SIZE) : fallback(PREVIEW_SIZE, 4));
        } catch (IOException e) {
            singleLabel.setIcon(fallback(PREVIEW_SIZE, 4));
        }
    }

    public void setImage(byte[] imageData) {
        cancelThumbLoader();
        inGridMode = false;
        switchTo(singleContainer);
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageData));
            singleLabel.setIcon(img != null ? scaled(img, PREVIEW_SIZE) : fallback(PREVIEW_SIZE, 4));
        } catch (IOException e) {
            singleLabel.setIcon(fallback(PREVIEW_SIZE, 4));
        }
    }

    // -------------------------------------------------------------------------
    // Grid (folder) mode
    // -------------------------------------------------------------------------

    /**
     * Shows a scrollable thumbnail grid for the supplied image files.
     * Placeholders appear immediately; images are decoded only for the visible
     * viewport region (plus {@link #OVERSCAN_ROWS} rows of lookahead). Thumbnails
     * that scroll far out of view are evicted to release memory.
     */
    public void setImages(List<File> files) {
        cancelThumbLoader();
        removeViewportListener();
        decoded.clear();

        currentFiles  = files;
        currentLabels = new ArrayList<>(files.size());
        inGridMode    = true;
        gridPanel.removeAll();
        switchTo(gridPanel);

        if (files.isEmpty()) {
            gridPanel.revalidate();
            return;
        }

        int ts = thumbSize;
        int maxLabelChars = ts >= 128 ? 20 : ts >= 96 ? 14 : 10;
        ImageIcon placeholder = fallback(ts, 2);

        for (File f : files) {
            JLabel lbl = new JLabel(truncate(f.getName(), maxLabelChars), placeholder, SwingConstants.CENTER);
            lbl.setVerticalTextPosition(SwingConstants.BOTTOM);
            lbl.setHorizontalTextPosition(SwingConstants.CENTER);
            lbl.setPreferredSize(new Dimension(ts + 20, ts + 30));
            lbl.setToolTipText(f.getName());
            lbl.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) showImageInfoDialog(f);
                }
            });
            gridPanel.add(lbl);
            currentLabels.add(lbl);
        }
        gridPanel.revalidate();
        gridPanel.repaint();

        // Watch viewport changes and trigger load/evict cycles
        viewportListener = e -> loadVisibleThumbnails();
        scrollPane.getViewport().addChangeListener(viewportListener);
        // Defer the first load until the layout pass has run and bounds are valid
        SwingUtilities.invokeLater(this::loadVisibleThumbnails);
    }

    // -------------------------------------------------------------------------
    // Viewport-bounded load / evict
    // -------------------------------------------------------------------------

    private void loadVisibleThumbnails() {
        if (currentLabels.isEmpty()) return;
        Rectangle visible = scrollPane.getViewport().getViewRect();
        int rowH      = thumbSize + 30;
        int overscanPx = OVERSCAN_ROWS * rowH;

        int yTop    = Math.max(0, visible.y - overscanPx);
        int yBottom = visible.y + visible.height + overscanPx;
        int evictPx = overscanPx * 4;

        List<Integer> toLoad = new ArrayList<>();
        for (int i = 0; i < currentLabels.size(); i++) {
            Rectangle b = currentLabels.get(i).getBounds();
            boolean inRange = (b.height == 0) // not yet laid out — include speculatively
                    || (b.y + b.height >= yTop && b.y <= yBottom);
            if (inRange && !decoded.get(i)) {
                toLoad.add(i);
            } else if (!inRange && decoded.get(i)) {
                // Evict thumbnails that are far out of view
                if (b.y + b.height < visible.y - evictPx || b.y > visible.y + visible.height + evictPx) {
                    currentLabels.get(i).setIcon(fallback(thumbSize, 2));
                    decoded.clear(i);
                }
            }
        }

        if (!toLoad.isEmpty()) startThumbLoader(toLoad);
    }

    private void startThumbLoader(List<Integer> indices) {
        cancelThumbLoader();
        List<File>   snapFiles  = List.copyOf(currentFiles);
        List<JLabel> snapLabels = List.copyOf(currentLabels);
        int          ts         = thumbSize;

        thumbLoader = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (int i : indices) {
                    if (isCancelled() || i >= snapFiles.size()) break;
                    try {
                        BufferedImage img = ImageIO.read(snapFiles.get(i));
                        if (img != null) publish(new ThumbResult(snapLabels.get(i), scaled(img, ts), i));
                    } catch (Exception ignored) {}
                }
                return null;
            }

            @Override
            protected void process(List<ThumbResult> chunks) {
                for (ThumbResult r : chunks) {
                    if (r.index() < currentLabels.size()
                            && r.label() == currentLabels.get(r.index())) {
                        r.label().setIcon(r.icon());
                        decoded.set(r.index());
                    }
                }
                gridPanel.repaint();
            }
        };
        thumbLoader.execute();
    }

    private void removeViewportListener() {
        if (viewportListener != null) {
            scrollPane.getViewport().removeChangeListener(viewportListener);
            viewportListener = null;
        }
    }

    // -------------------------------------------------------------------------
    // i18n
    // -------------------------------------------------------------------------

    /** Refreshes the titled border text after a locale change. */
    public void applyI18n() {
        setBorder(BorderFactory.createTitledBorder(Messages.get("label.preview")));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void switchTo(JPanel content) {
        if (scrollPane.getViewport().getView() != content) {
            scrollPane.setViewportView(content);
        }
    }

    private void cancelThumbLoader() {
        if (thumbLoader != null && !thumbLoader.isDone()) {
            thumbLoader.cancel(true);
        }
    }

    private static ImageIcon scaled(BufferedImage src, int size) {
        return new ImageIcon(src.getScaledInstance(size, size, Image.SCALE_SMOOTH));
    }

    private static ImageIcon fallback(int size, int stroke) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, size, size);
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.drawLine(0, 0, size, size);
        g.drawLine(size, 0, 0, size);
        g.dispose();
        return new ImageIcon(img);
    }

    private static String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "\u2026";
    }

    // -------------------------------------------------------------------------
    // BLP info popup
    // -------------------------------------------------------------------------

    /**
     * Opens a dialog showing the image at its natural pixel dimensions together with
     * file/format metadata. Format-specific handling:
     * <ul>
     *   <li><b>BLP</b> – uses the BLP ImageReader for mipmap count and format name.</li>
     *   <li><b>DDS</b> – parses the binary DDS header (dimensions, mipmap count, FourCC
     *       format). Preview is attempted via {@link ImageIO}; shown as unavailable when the
     *       codec is missing (DXT-compressed DDS requires an extra plugin).</li>
     *   <li><b>All others</b> – loaded via {@link ImageIO} (PNG, JPG, BMP, GIF, TGA with
     *       TwelveMonkeys on the classpath).</li>
     * </ul>
     */
    private void showImageInfoDialog(File file) {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.anchor = GridBagConstraints.NORTHWEST;
        lc.insets = new Insets(2, 4, 2, 10);

        GridBagConstraints vc = new GridBagConstraints();
        vc.gridx = 1; vc.anchor = GridBagConstraints.NORTHWEST; vc.weightx = 1.0;
        vc.insets = new Insets(2, 0, 2, 4);

        int[] row = {0};
        addInfoRow(infoPanel, lc, vc, row[0]++, "File:",         file.getName());
        addInfoRow(infoPanel, lc, vc, row[0]++, "Size on disk:", formatFileSize(file.length()));

        GridBagConstraints sep = new GridBagConstraints();
        sep.gridx = 0; sep.gridy = row[0]++; sep.gridwidth = 2;
        sep.fill = GridBagConstraints.HORIZONTAL; sep.insets = new Insets(4, 0, 4, 0);
        infoPanel.add(new JSeparator(), sep);

        String ext = getExtension(file.getName());
        BufferedImage[] imgRef = {null};

        if ("blp".equals(ext)) {
            // BLP: use the dedicated ImageReader for rich metadata (mipmap levels, format name)
            try {
                Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("blp");
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
                        if (iis == null) throw new IOException("Cannot create image stream");
                        reader.setInput(iis, false, false);
                        addInfoRow(infoPanel, lc, vc, row[0]++, "Dimensions:",
                                reader.getWidth(0) + " x " + reader.getHeight(0) + " px");
                        try {
                            addInfoRow(infoPanel, lc, vc, row[0]++, "Mipmap levels:",
                                    String.valueOf(reader.getNumImages(true)));
                        } catch (IOException ignored) {
                            addInfoRow(infoPanel, lc, vc, row[0]++, "Mipmap levels:", "N/A");
                        }
                        String fmt = reader.getFormatName();
                        if (fmt != null) addInfoRow(infoPanel, lc, vc, row[0]++, "Format:", fmt);
                        try { imgRef[0] = reader.read(0); } catch (Exception ignored) {}
                    } finally {
                        reader.dispose();
                    }
                } else {
                    imgRef[0] = ImageIO.read(file);
                    if (imgRef[0] != null)
                        addInfoRow(infoPanel, lc, vc, row[0]++, "Dimensions:",
                                imgRef[0].getWidth() + " x " + imgRef[0].getHeight() + " px");
                    addInfoRow(infoPanel, lc, vc, row[0]++, "Note:", "BLP reader not available");
                }
            } catch (Exception ex) {
                addInfoRow(infoPanel, lc, vc, row[0]++, "Error:", ex.getMessage());
            }

        } else if ("dds".equals(ext)) {
            // DDS: read the 128-byte binary header to extract metadata without a full codec
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] hdr = new byte[128];
                if (fis.read(hdr) >= 128
                        && hdr[0] == 'D' && hdr[1] == 'D' && hdr[2] == 'S' && hdr[3] == ' ') {
                    int height  = readLE32(hdr, 12);
                    int width   = readLE32(hdr, 16);
                    int mipmaps = readLE32(hdr, 28);
                    addInfoRow(infoPanel, lc, vc, row[0]++, "Dimensions:", width + " x " + height + " px");
                    if (mipmaps > 0)
                        addInfoRow(infoPanel, lc, vc, row[0]++, "Mipmap levels:", String.valueOf(mipmaps));
                    // DDPIXELFORMAT starts at offset 76; dwFlags at 80, dwFourCC at 84
                    int pxFlags = readLE32(hdr, 80);
                    if ((pxFlags & 0x4) != 0) { // DDPF_FOURCC
                        addInfoRow(infoPanel, lc, vc, row[0]++, "Format:",
                                new String(hdr, 84, 4).trim());
                    } else {
                        addInfoRow(infoPanel, lc, vc, row[0]++, "Format:",
                                "Uncompressed " + readLE32(hdr, 88) + "-bit");
                    }
                }
            } catch (Exception ignored) {}
            // Attempt preview via ImageIO (succeeds only if a DDS codec is on the classpath)
            try { imgRef[0] = ImageIO.read(file); } catch (Exception ignored) {}
            if (imgRef[0] == null)
                addInfoRow(infoPanel, lc, vc, row[0]++, "Preview:",
                        "DDS compressed format — preview unavailable");

        } else {
            // Generic: PNG, JPG, TGA (TwelveMonkeys), BMP, GIF, …
            try {
                imgRef[0] = ImageIO.read(file);
                if (imgRef[0] != null)
                    addInfoRow(infoPanel, lc, vc, row[0]++, "Dimensions:",
                            imgRef[0].getWidth() + " x " + imgRef[0].getHeight() + " px");
            } catch (Exception ex) {
                addInfoRow(infoPanel, lc, vc, row[0]++, "Error:", ex.getMessage());
            }
        }

        // ---- assemble dialog: image on top (1:1 pixels, capped at 512), metadata below ----
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        if (imgRef[0] != null) {
            BufferedImage img = imgRef[0];
            JLabel imgLabel = new JLabel(new ImageIcon(img));
            imgLabel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            JScrollPane imgScroll = new JScrollPane(imgLabel,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            imgScroll.setBorder(null);
            imgScroll.setPreferredSize(new Dimension(
                    Math.min(img.getWidth()  + 4, 512),
                    Math.min(img.getHeight() + 4, 512)));
            content.add(imgScroll, BorderLayout.CENTER);
        }

        content.add(infoPanel, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                content,
                "Image Info - " + file.getName(),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    /** Reads a little-endian 32-bit int from {@code data} at {@code offset}. */
    private static int readLE32(byte[] data, int offset) {
        return (data[offset]     & 0xFF)
             | ((data[offset + 1] & 0xFF) << 8)
             | ((data[offset + 2] & 0xFF) << 16)
             | ((data[offset + 3] & 0xFF) << 24);
    }

    /** Adds a bold label + value row to a GridBagLayout panel. */
    private static void addInfoRow(JPanel p,
                                   GridBagConstraints lc, GridBagConstraints vc,
                                   int row, String label, String value) {
        lc.gridy = row;
        vc.gridy = row;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, lc);
        p.add(new JLabel(value), vc);
    }

    /** Returns a human-readable byte count (B / KB / MB). */
    private static String formatFileSize(long bytes) {
        if (bytes < 1_024)           return bytes + " B";
        if (bytes < 1_024 * 1_024)   return String.format("%.1f KB", bytes / 1_024.0);
        return String.format("%.1f MB", bytes / (1_024.0 * 1_024));
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * A {@link JPanel} with {@link FlowLayout} that implements {@link Scrollable}.
     *
     * <p>Two things work together to give a proper wrapping grid inside a
     * {@link JScrollPane}:
     * <ul>
     *   <li>{@code getScrollableTracksViewportWidth() = true} — scroll pane sets this
     *       panel's width to the viewport width, so {@link FlowLayout#layoutContainer}
     *       wraps items visually at the right boundary.</li>
     *   <li>{@code getPreferredSize()} simulates the same wrapping and returns the
     *       correct total height — without this, {@link FlowLayout#preferredLayoutSize}
     *       would report a single-row height and the scroll pane would never scroll.</li>
     * </ul>
     *
     * <p>Non-static inner class so it can read the viewport width from the outer
     * {@link PreviewPanel#scrollPane}.
     */
    private class ScrollableFlowPanel extends JPanel implements Scrollable {

        ScrollableFlowPanel() {
            super(new FlowLayout(FlowLayout.LEFT, 6, 6));
        }

        /**
         * Returns the correctly wrapped preferred size.
         * Width is the current viewport width; height is computed by simulating
         * the same row-breaking logic that {@link FlowLayout#layoutContainer} uses.
         */
        @Override
        public Dimension getPreferredSize() {
            int viewportWidth = scrollPane.getViewport().getWidth();
            if (viewportWidth <= 0) {
                // Viewport not yet laid out — fall back to default (single-row estimate)
                return super.getPreferredSize();
            }
            return new Dimension(viewportWidth, computeWrappedHeight(viewportWidth));
        }

        /** Simulates FlowLayout row-breaking to find the total height needed. */
        private int computeWrappedHeight(int availableWidth) {
            FlowLayout fl = (FlowLayout) getLayout();
            int hgap = fl.getHgap();
            int vgap = fl.getVgap();

            int x = hgap;
            int totalHeight = vgap;
            int rowHeight = 0;

            for (Component c : getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                if (x + d.width + hgap > availableWidth && x > hgap) {
                    // Start a new row
                    totalHeight += rowHeight + vgap;
                    x = hgap;
                    rowHeight = 0;
                }
                x += d.width + hgap;
                rowHeight = Math.max(rowHeight, d.height);
            }
            totalHeight += rowHeight + vgap; // last row
            return totalHeight;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return thumbSize + 30; // one thumbnail row
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return (thumbSize + 30) * 2;
        }

        /** Forces panel width == viewport width so FlowLayout wraps at the right boundary. */
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        /** Allows panel height to grow beyond the viewport, enabling vertical scrolling. */
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private record ThumbResult(JLabel label, ImageIcon icon, int index) {}
}
