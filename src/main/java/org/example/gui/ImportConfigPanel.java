package org.example.gui;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * "Import Configuration" tab panel.
 *
 * <p>Split horizontally:
 * <ul>
 *   <li><b>Left</b>  – configuration form (origin ID, angle, spacing, …)</li>
 *   <li><b>Right</b> – {@link MapPreviewPanel} with a drawing-mode toolbar</li>
 * </ul>
 *
 * Configuration changes are propagated to the {@link MapPreviewPanel} immediately
 * so the triangle / unit grid updates live while the user adjusts values.
 */
public class ImportConfigPanel extends JPanel {

    // -------------------------------------------------------------------------
    // Child components
    // -------------------------------------------------------------------------

    private final MapPreviewPanel mapPreviewPanel = new MapPreviewPanel();

    // ---- Form fields ----
    private final JTextField   unitOriginIdField;
    private final JSpinner     unitAngleSpinner;
    private final JSpinner     unitSpacingSpinner;
    private final JCheckBox    showTrianglesBox;
    private final JComboBox<MapPreviewPanel.PlacingOrder> placingOrderCombo;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public ImportConfigPanel() {
        setLayout(new BorderLayout());

        // Unit origin ID – exactly 4 characters
        unitOriginIdField = new JTextField("x000", 6);
        ((AbstractDocument) unitOriginIdField.getDocument()).setDocumentFilter(
                new MaxLengthFilter(4));

        unitAngleSpinner   = new JSpinner(new SpinnerNumberModel(270.0,   0.0, 360.0,  1.0));
        unitSpacingSpinner = new JSpinner(new SpinnerNumberModel(  64.0,  1.0, 5000.0, 8.0));
        showTrianglesBox   = new JCheckBox("Show triangles", true);
        placingOrderCombo  = new JComboBox<>(MapPreviewPanel.PlacingOrder.values());

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, buildConfigPanel(), buildMapPanel());
        split.setDividerLocation(230);
        split.setResizeWeight(0.0);
        add(split, BorderLayout.CENTER);

        // Attach listeners and do initial sync
        unitAngleSpinner  .addChangeListener(e -> syncConfig());
        unitSpacingSpinner.addChangeListener(e -> syncConfig());
        showTrianglesBox  .addActionListener(e -> syncConfig());
        placingOrderCombo .addActionListener(e -> syncConfig());
        syncConfig();
    }

    // -------------------------------------------------------------------------
    // Panel builders
    // -------------------------------------------------------------------------

    private JPanel buildConfigPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Configuration"));

        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(5, 10, 3, 5);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(5, 2, 3, 10);

        int row = 0;
        addRow(p, lc, fc, row++, "Unit Origin ID:",  unitOriginIdField);
        addRow(p, lc, fc, row++, "Unit Angle (°):",  unitAngleSpinner);
        addRow(p, lc, fc, row++, "Unit Spacing:",     unitSpacingSpinner);

        // Checkbox spans both columns
        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0; cc.gridy = row++; cc.gridwidth = 2;
        cc.anchor = GridBagConstraints.WEST;
        cc.insets = new Insets(8, 10, 3, 10);
        p.add(showTrianglesBox, cc);

        addRow(p, lc, fc, row++, "Placing Order:", placingOrderCombo);

        // Vertical filler pushes fields to the top
        GridBagConstraints fill = new GridBagConstraints();
        fill.gridx = 0; fill.gridy = row; fill.gridwidth = 2; fill.weighty = 1.0;
        p.add(Box.createVerticalGlue(), fill);

        return p;
    }

    private JPanel buildMapPanel() {
        JPanel p = new JPanel(new BorderLayout());

        // Drawing-mode toolbar
        JToggleButton rectBtn   = new JToggleButton("Rectangle", true);
        JToggleButton circleBtn = new JToggleButton("Circle");
        ButtonGroup   bg        = new ButtonGroup();
        bg.add(rectBtn);
        bg.add(circleBtn);

        rectBtn  .addActionListener(e -> mapPreviewPanel.setDrawingMode(MapPreviewPanel.DrawingMode.RECTANGLE));
        circleBtn.addActionListener(e -> mapPreviewPanel.setDrawingMode(MapPreviewPanel.DrawingMode.CIRCLE));

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> mapPreviewPanel.clearShape());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(rectBtn);
        toolbar.add(circleBtn);
        toolbar.addSeparator();
        toolbar.add(clearBtn);

        p.add(toolbar,         BorderLayout.NORTH);
        p.add(mapPreviewPanel, BorderLayout.CENTER);
        return p;
    }

    private static void addRow(JPanel p,
                               GridBagConstraints lc, GridBagConstraints fc,
                               int row, String label, JComponent field) {
        lc.gridy = row;
        fc.gridy = row;
        p.add(new JLabel(label), lc);
        p.add(field,             fc);
    }

    // -------------------------------------------------------------------------
    // Config sync
    // -------------------------------------------------------------------------

    private void syncConfig() {
        mapPreviewPanel.setUnitAngle   (((Number) unitAngleSpinner  .getValue()).doubleValue());
        mapPreviewPanel.setUnitSpacing (((Number) unitSpacingSpinner.getValue()).doubleValue());
        mapPreviewPanel.setShowTriangles(showTrianglesBox.isSelected());
        mapPreviewPanel.setPlacingOrder((MapPreviewPanel.PlacingOrder) placingOrderCombo.getSelectedItem());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Called by MainFrame when a map is opened. */
    public void setMapPreviewImage(BufferedImage image) {
        mapPreviewPanel.setMapImage(image);
    }

    // Getters for the import pipeline
    public String  getUnitOriginId()  { return unitOriginIdField.getText(); }
    public double  getUnitAngle()     { return ((Number) unitAngleSpinner  .getValue()).doubleValue(); }
    public double  getUnitSpacing()   { return ((Number) unitSpacingSpinner.getValue()).doubleValue(); }
    public boolean isShowTriangles()  { return showTrianglesBox.isSelected(); }
    public MapPreviewPanel.PlacingOrder getPlacingOrder() {
        return (MapPreviewPanel.PlacingOrder) placingOrderCombo.getSelectedItem();
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** DocumentFilter that rejects input exceeding a maximum character count. */
    private static class MaxLengthFilter extends DocumentFilter {
        private final int max;

        MaxLengthFilter(int max) { this.max = max; }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            if (text != null && fb.getDocument().getLength() + text.length() <= max) {
                super.insertString(fb, offset, text, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text != null && fb.getDocument().getLength() - length + text.length() <= max) {
                super.replace(fb, offset, length, text, attrs);
            }
        }
    }
}
