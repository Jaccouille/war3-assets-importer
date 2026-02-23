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
    private static final int THUMB_SIZE   = 96;

    // ---- Single-image mode ----
    private final JLabel singleLabel     = new JLabel();
    private final JPanel singleContainer;

    // ---- Grid mode ----
    // Non-static inner class so it can read scrollPane.getViewport().getWidth()
    private final ScrollableFlowPanel gridPanel = new ScrollableFlowPanel();

    // ---- Shared scroll pane (always present) ----
    private final JScrollPane scrollPane;

    // ---- Async thumb loading ----
    private SwingWorker<?, ?> thumbLoader;

    public PreviewPanel() {
        setLayout(new BorderLayout());

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

    // -------------------------------------------------------------------------
    // Single-image setters
    // -------------------------------------------------------------------------

    public void setImage(BufferedImage image) {
        cancelThumbLoader();
        switchTo(singleContainer);
        singleLabel.setIcon(image != null
                ? scaled(image, PREVIEW_SIZE)
                : fallback(PREVIEW_SIZE, 4));
    }

    public void setImage(File file) {
        cancelThumbLoader();
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
     * Placeholders appear immediately; actual images load on a background thread.
     * Any previously running load is cancelled first.
     */
    public void setImages(List<File> files) {
        cancelThumbLoader();
        gridPanel.removeAll();
        switchTo(gridPanel);

        if (files.isEmpty()) {
            gridPanel.revalidate();
            return;
        }

        // Create placeholder labels synchronously so the layout is ready immediately.
        List<JLabel> labels = new ArrayList<>(files.size());
        ImageIcon placeholder = fallback(THUMB_SIZE, 2);
        for (File f : files) {
            JLabel lbl = new JLabel(truncate(f.getName(), 14), placeholder, SwingConstants.CENTER);
            lbl.setVerticalTextPosition(SwingConstants.BOTTOM);
            lbl.setHorizontalTextPosition(SwingConstants.CENTER);
            lbl.setPreferredSize(new Dimension(THUMB_SIZE + 20, THUMB_SIZE + 30));
            lbl.setToolTipText(f.getName());

            // Double-click → show BLP metadata popup
            lbl.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) showBlpInfoDialog(f);
                }
            });

            gridPanel.add(lbl);
            labels.add(lbl);
        }
        gridPanel.revalidate();
        gridPanel.repaint();

        // Load actual images in the background and publish results to the EDT.
        thumbLoader = new SwingWorker<Void, ThumbResult>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < files.size() && !isCancelled(); i++) {
                    try {
                        BufferedImage img = ImageIO.read(files.get(i));
                        if (img != null) {
                            publish(new ThumbResult(labels.get(i), scaled(img, THUMB_SIZE)));
                        }
                    } catch (Exception ignored) {}
                }
                return null;
            }

            @Override
            protected void process(List<ThumbResult> chunks) {
                for (ThumbResult r : chunks) {
                    r.label().setIcon(r.icon());
                }
                gridPanel.repaint();
            }
        };
        thumbLoader.execute();
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
     * Opens a small dialog showing metadata for {@code file}:
     * filename, file size, pixel dimensions, and mipmap count.
     * Uses the registered BLP {@link ImageReader} when available.
     */
    private void showBlpInfoDialog(File file) {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.anchor = GridBagConstraints.NORTHWEST;
        lc.insets = new Insets(2, 4, 2, 10);

        GridBagConstraints vc = new GridBagConstraints();
        vc.gridx = 1; vc.anchor = GridBagConstraints.NORTHWEST; vc.weightx = 1.0;
        vc.insets = new Insets(2, 0, 2, 4);

        int[] row = {0};

        addInfoRow(infoPanel, lc, vc, row[0]++, "File:",          file.getName());
        addInfoRow(infoPanel, lc, vc, row[0]++, "Size on disk:",  formatFileSize(file.length()));

        // Separator
        GridBagConstraints sep = new GridBagConstraints();
        sep.gridx = 0; sep.gridy = row[0]++; sep.gridwidth = 2;
        sep.fill = GridBagConstraints.HORIZONTAL; sep.insets = new Insets(4, 0, 4, 0);
        infoPanel.add(new JSeparator(), sep);

        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("blp");
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
                    if (iis == null) throw new IOException("Cannot create image stream");
                    reader.setInput(iis, false, false);

                    int w = reader.getWidth(0);
                    int h = reader.getHeight(0);
                    addInfoRow(infoPanel, lc, vc, row[0]++, "Dimensions:", w + " × " + h + " px");

                    try {
                        int mipmaps = reader.getNumImages(true);
                        addInfoRow(infoPanel, lc, vc, row[0]++, "Mipmap levels:", String.valueOf(mipmaps));
                    } catch (IOException ex) {
                        addInfoRow(infoPanel, lc, vc, row[0]++, "Mipmap levels:", "N/A");
                    }

                    addInfoRow(infoPanel, lc, vc, row[0]++, "Format:",
                            reader.getFormatName() != null ? reader.getFormatName() : "BLP");
                } finally {
                    reader.dispose();
                }
            } else {
                // BLP reader not registered — fall back to plain ImageIO
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    addInfoRow(infoPanel, lc, vc, row[0]++, "Dimensions:",
                            img.getWidth() + " × " + img.getHeight() + " px");
                }
                addInfoRow(infoPanel, lc, vc, row[0]++, "Note:", "BLP reader not available");
            }
        } catch (Exception ex) {
            addInfoRow(infoPanel, lc, vc, row[0]++, "Error:", ex.getMessage());
        }

        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                infoPanel,
                "BLP Image Info — " + file.getName(),
                JOptionPane.INFORMATION_MESSAGE);
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
            return THUMB_SIZE + 30; // one thumbnail row
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return (THUMB_SIZE + 30) * 2;
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

    private record ThumbResult(JLabel label, ImageIcon icon) {}
}
