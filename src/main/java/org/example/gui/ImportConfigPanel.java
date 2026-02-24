package org.example.gui;

import org.example.gui.i18n.Messages;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

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

    // ---- Unit placement form fields ----
    private final JTextField   unitOriginIdField;
    private final JSpinner     unitAngleSpinner;
    private final JSpinner     unitSpacingSpinner;
    private final JCheckBox    showTrianglesBox;
    private final JComboBox<MapPreviewPanel.PlacingOrder> placingOrderCombo;

    // ---- Unit creation form fields (moved from MapOptionsPanel) ----
    private final JCheckBox    createUnitsBox;
    private final JCheckBox    placeUnitsBox;
    private final JCheckBox    clearUnitsBox;
    private final JCheckBox    clearAssetsBox;
    private final JSpinner     unitScalingSpinner;

    // ---- Unit naming fields ----
    private final JCheckBox    autoNameUnitsBox;
    private final JComboBox<String> nameFormatCombo;

    // ---- Caching for preview ----
    private java.util.List<String> selectedMdxFilenames = java.util.Collections.emptyList();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public ImportConfigPanel() {
        setLayout(new BorderLayout());

        // ---- Unit placement fields ----
        unitOriginIdField = new JTextField("x000", 6);
        ((AbstractDocument) unitOriginIdField.getDocument()).setDocumentFilter(
                new MaxLengthFilter(4));

        unitAngleSpinner   = new JSpinner(new SpinnerNumberModel(270.0,   0.0, 360.0,  1.0));
        unitSpacingSpinner = new JSpinner(new SpinnerNumberModel( 64.0,  1.0, 5000.0, 8.0));
        showTrianglesBox   = new JCheckBox("Show triangles", true);
        placingOrderCombo  = new JComboBox<>(MapPreviewPanel.PlacingOrder.values());

        // ---- Unit creation fields ----
        createUnitsBox = new JCheckBox();
        placeUnitsBox = new JCheckBox();
        clearUnitsBox = new JCheckBox();
        clearAssetsBox = new JCheckBox();
        unitScalingSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 10.0, 0.1));

        // ---- Unit naming fields ----
        autoNameUnitsBox = new JCheckBox("Auto-name from MDX filename");
        nameFormatCombo = new JComboBox<>(new String[]{
                "Space Separated",
                "camelCase",
                "snake_case",
                "UPPER_CASE"
        });

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, buildConfigPanel(), buildMapPanel());
        split.setDividerLocation(280);
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
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));

        // ---- Unit Creation Section ----
        main.add(buildCreationSection());
        main.add(Box.createVerticalStrut(10));

        // ---- Unit Placement Section ----
        main.add(buildPlacementSection());
        main.add(Box.createVerticalStrut(10));

        // ---- Unit Naming Section ----
        main.add(buildNamingSection());

        // Filler at bottom
        main.add(Box.createVerticalGlue());

        // Wrap in scrollpane for overflow handling
        JScrollPane scroll = new JScrollPane(main);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Return the main panel (scroll pane is optional, panel handles layout naturally)
        return main;
    }

    private JPanel buildCreationSection() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Unit Creation"));

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

        // Checkboxes spanning both columns
        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0; cc.gridwidth = 2;
        cc.anchor = GridBagConstraints.WEST;
        cc.insets = new Insets(3, 10, 3, 10);
        cc.gridy = row++;
        createUnitsBox.setText(Messages.get("checkbox.createUnits"));
        p.add(createUnitsBox, cc);

        cc.gridy = row++;
        placeUnitsBox.setText(Messages.get("checkbox.placeUnits"));
        p.add(placeUnitsBox, cc);

        cc.gridy = row++;
        clearUnitsBox.setText(Messages.get("checkbox.clearUnits"));
        p.add(clearUnitsBox, cc);

        cc.gridy = row++;
        clearAssetsBox.setText(Messages.get("checkbox.clearAssets"));
        p.add(clearAssetsBox, cc);

        addRow(p, lc, fc, row++, "Unit Scaling:", unitScalingSpinner);

        return p;
    }

    private JPanel buildPlacementSection() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Unit Placement"));

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

        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0; cc.gridy = row++; cc.gridwidth = 2;
        cc.anchor = GridBagConstraints.WEST;
        cc.insets = new Insets(8, 10, 3, 10);
        p.add(showTrianglesBox, cc);

        addRow(p, lc, fc, row++, "Placing Order:", placingOrderCombo);

        return p;
    }

    private JPanel buildNamingSection() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Unit Naming"));

        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0; cc.gridy = 0; cc.gridwidth = 2;
        cc.anchor = GridBagConstraints.WEST;
        cc.insets = new Insets(5, 10, 5, 10);
        p.add(autoNameUnitsBox, cc);

        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(3, 10, 3, 5);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(3, 2, 3, 10);

        addRow(p, lc, fc, 1, "Format:", nameFormatCombo);

        JButton previewBtn = new JButton("Preview");
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0; bc.gridy = 2; bc.gridwidth = 2;
        bc.anchor = GridBagConstraints.CENTER;
        bc.insets = new Insets(8, 10, 5, 10);
        previewBtn.addActionListener(e -> showNamingPreview());
        p.add(previewBtn, bc);

        return p;
    }

    private void showNamingPreview() {
        if (selectedMdxFilenames.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No MDX files selected. Please select MDX files in the Assets tab first.",
                    "Unit Naming Preview",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String format = getNameFormat();
        String[] columnNames = {"Original Name", "Formatted Name"};
        Object[][] data = new Object[selectedMdxFilenames.size()][2];

        for (int i = 0; i < selectedMdxFilenames.size(); i++) {
            String filename = selectedMdxFilenames.get(i);
            String formatted = NameFormatter.format(filename, format);
            data[i][0] = removeExtension(filename);
            data[i][1] = formatted;
        }

        JTable table = new JTable(data, columnNames);
        table.setDefaultEditor(Object.class, null);  // Make read-only
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(400, Math.min(300, selectedMdxFilenames.size() * 24 + 30)));

        JOptionPane.showMessageDialog(this,
                scrollPane,
                "Unit Naming Preview - " + format,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
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
        addRow(p, lc, fc, row, new JLabel(label), field);
    }

    private static void addRow(JPanel p,
                               GridBagConstraints lc, GridBagConstraints fc,
                               int row, JLabel label, JComponent field) {
        lc.gridy = row;
        fc.gridy = row;
        p.add(label, lc);
        p.add(field, fc);
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

    /** Sets the list of selected MDX filenames for the naming preview. */
    public void setSelectedMdxFilenames(java.util.List<String> filenames) {
        this.selectedMdxFilenames = filenames != null ? filenames : java.util.Collections.emptyList();
    }

    // ---- Unit Creation Getters ----
    public boolean isCreateUnitsSelected()  { return createUnitsBox.isSelected(); }
    public boolean isPlaceUnitsSelected()   { return placeUnitsBox.isSelected(); }
    public boolean isClearUnitsSelected()   { return clearUnitsBox.isSelected(); }
    public boolean isClearAssetsSelected()  { return clearAssetsBox.isSelected(); }
    public double  getUnitScaling()         { return ((Number) unitScalingSpinner.getValue()).doubleValue(); }

    // ---- Unit Placement Getters ----
    public String  getUnitOriginId()  { return unitOriginIdField.getText(); }
    public double  getUnitAngle()     { return ((Number) unitAngleSpinner  .getValue()).doubleValue(); }
    public double  getUnitSpacing()   { return ((Number) unitSpacingSpinner.getValue()).doubleValue(); }
    public boolean isShowTriangles()  { return showTrianglesBox.isSelected(); }
    public MapPreviewPanel.PlacingOrder getPlacingOrder() {
        return (MapPreviewPanel.PlacingOrder) placingOrderCombo.getSelectedItem();
    }

    // ---- Unit Naming Getters ----
    public boolean isAutoNameUnitsEnabled() { return autoNameUnitsBox.isSelected(); }
    public String  getNameFormat()          { return (String) nameFormatCombo.getSelectedItem(); }

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
