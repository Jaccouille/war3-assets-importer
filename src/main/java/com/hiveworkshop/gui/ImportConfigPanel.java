package org.example.gui;

import org.example.core.model.ExistingUnit;
import org.example.core.model.ImportOptions;
import org.example.core.model.UnitEntry;
import org.example.core.util.CameraBounds;
import org.example.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * "Import Configuration" tab panel.
 *
 * <p>Split horizontally:
 * <ul>
 *   <li><b>Left</b>  – configuration form (origin ID, angle, spacing, …)</li>
 *   <li><b>Right</b> – {@link MapPreviewPanel} with a drawing-mode toolbar and
 *       a capacity status bar at the bottom</li>
 * </ul>
 * <p>
 * Configuration changes are propagated to the {@link MapPreviewPanel} immediately
 * so the triangle / unit grid updates live while the user adjusts values.
 */
public class ImportConfigPanel extends JPanel {

    // -------------------------------------------------------------------------
    // Child components
    // -------------------------------------------------------------------------

    private final MapPreviewPanel mapPreviewPanel = new MapPreviewPanel();

    // ---- Unit placement form fields ----
    private final UnitSuggestionField unitOriginField;
    private final JSpinner unitAngleSpinner;
    private final JSpinner unitSpacingXSpinner;
    private final JSpinner unitSpacingYSpinner;
    private final JComboBox<MapPreviewPanel.PlacingOrder> placingOrderCombo;

    // ---- Unit creation form fields (moved from MapOptionsPanel) ----
    private final JCheckBox createUnitsBox;
    private final JCheckBox placeUnitsBox;
    private final JCheckBox clearUnitsBox;
    private final JCheckBox clearAssetsBox;
    private final JSpinner unitScalingSpinner;

    // ---- General import options ----
    private final JCheckBox flattenPathsBox;

    // ---- Unit naming fields ----
    private final JCheckBox autoNameUnitsBox;
    private final JComboBox<NameFormatItem> nameFormatCombo;
    private final JCheckBox autoAssignIconBox;

    // ---- Section panels (stored for i18n border refresh) ----
    private JPanel creationSection;
    private JPanel placementSection;
    private JPanel namingSection;

    // ---- Row labels (stored for i18n refresh) ----
    private final JLabel unitScalingLabel  = new JLabel();
    private final JLabel unitOriginIdLabel = new JLabel();
    private final JLabel unitAngleLabel    = new JLabel();
    private final JLabel spacingXLabel     = new JLabel();
    private final JLabel spacingYLabel     = new JLabel();
    private final JLabel placingOrderLabel = new JLabel();
    private final JLabel nameFormatLabel   = new JLabel();
    private JLabel outputLabel;

    // ---- Sub-panel buttons (stored for i18n refresh) ----
    private JButton previewBtn;
    private JButton clearBtn;
    private JButton browseBtn;

    // ---- Capacity status bar ----
    private final JLabel capacityIndicator;
    private final JLabel capacityLabel;
    private final JButton helpButton;

    // ---- Output path ----
    private final JTextField outputPathField;

    // ---- Caching for preview ----
    private java.util.List<String> selectedMdxFilenames = java.util.Collections.emptyList();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public ImportConfigPanel() {
        setLayout(new BorderLayout());

        // ---- Unit placement fields ----
        unitOriginField = new UnitSuggestionField("hfoo");

        unitAngleSpinner = new JSpinner(new SpinnerNumberModel(270.0, 0.0, 360.0, 15.0));
        unitSpacingXSpinner = new JSpinner(new SpinnerNumberModel(16.0, 1.0, 5000.0, 8.0));
        unitSpacingYSpinner = new JSpinner(new SpinnerNumberModel(16.0, 1.0, 5000.0, 8.0));
        placingOrderCombo = new JComboBox<>(MapPreviewPanel.PlacingOrder.values());

        // ---- Unit creation fields ----
        createUnitsBox = new JCheckBox();
        placeUnitsBox = new JCheckBox();
        clearUnitsBox = new JCheckBox();
        clearAssetsBox = new JCheckBox();
        flattenPathsBox = new JCheckBox(Messages.get("checkbox.flattenPaths"));
        unitScalingSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 10.0, 0.1));

        // ---- Unit naming fields ----
        autoNameUnitsBox = new JCheckBox(Messages.get("checkbox.autoNameUnits"));
        autoAssignIconBox = new JCheckBox(Messages.get("checkbox.autoAssignIcon"));
        nameFormatCombo = new JComboBox<>(new NameFormatItem[]{
                new NameFormatItem("Space Separated (keep case)", "nameFormat.spaceSeparatedKeepCase"),
                new NameFormatItem("Space Separated",             "nameFormat.spaceSeparated"),
                new NameFormatItem("camelCase",                   "nameFormat.camelCase"),
                new NameFormatItem("snake_case",                  "nameFormat.snakeCase"),
                new NameFormatItem("UPPER_CASE",                  "nameFormat.upperCase"),
        });

        // ---- Tooltips (sourced from i18n bundle) ----
        createUnitsBox.setToolTipText(Messages.get("tooltip.createUnits"));
        placeUnitsBox.setToolTipText(Messages.get("tooltip.placeUnits"));
        clearUnitsBox.setToolTipText(Messages.get("tooltip.clearUnits"));
        clearAssetsBox.setToolTipText(Messages.get("tooltip.clearAssets"));
        flattenPathsBox.setToolTipText(Messages.get("tooltip.flattenPaths"));
        unitScalingSpinner.setToolTipText(Messages.get("tooltip.unitScaling"));
        unitOriginField.setToolTipText(Messages.get("tooltip.unitOriginId"));
        unitAngleSpinner.setToolTipText(Messages.get("tooltip.unitAngle"));
        unitSpacingXSpinner.setToolTipText(Messages.get("tooltip.spacingX"));
        unitSpacingYSpinner.setToolTipText(Messages.get("tooltip.spacingY"));
        placingOrderCombo.setToolTipText(Messages.get("tooltip.placingOrder"));
        autoNameUnitsBox.setToolTipText(Messages.get("tooltip.autoNameUnits"));
        autoAssignIconBox.setToolTipText(Messages.get("tooltip.autoAssignIcon"));
        nameFormatCombo.setToolTipText(Messages.get("tooltip.nameFormat"));

        // ---- Output path field ----
        outputPathField = new JTextField();
        outputPathField.setToolTipText("Destination .w3x/.w3m file");

        // ---- Capacity status bar ----
        capacityIndicator = new JLabel();
        capacityIndicator.setPreferredSize(new Dimension(16, 16));
        capacityLabel = new JLabel(Messages.get("status.drawShape"));
        helpButton = new JButton("?");
        helpButton.setMargin(new Insets(1, 5, 1, 5));
        helpButton.setToolTipText(Messages.get("button.help"));
        helpButton.addActionListener(e -> showCapacityHelp());

        // Set initial indicator color (gray)
        updateIndicatorColor(Color.GRAY);

        // Wire capacity listener
        mapPreviewPanel.setCapacityListener((placed, total, capacity) ->
                SwingUtilities.invokeLater(() -> updateCapacityStatus(placed, total, capacity)));

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, buildConfigPanel(), buildMapPanel());
        split.setDividerLocation(280);
        split.setResizeWeight(0.0);
        add(split, BorderLayout.CENTER);

        // Attach listeners and do initial sync
        unitAngleSpinner.addChangeListener(e -> syncConfig());
        unitSpacingXSpinner.addChangeListener(e -> syncConfig());
        unitSpacingYSpinner.addChangeListener(e -> syncConfig());
        placingOrderCombo.addActionListener(e -> syncConfig());
        syncConfig();

        // Populate all i18n labels for the initial locale
        applyI18n();
    }

    // -------------------------------------------------------------------------
    // Panel builders
    // -------------------------------------------------------------------------

    /**
     * Extracts just the filename (no path, no extension) from a filepath.
     * E.g. "models/Infantry/InfantryAmerican.mdx" -> "InfantryAmerican"
     */
    private static String extractFilenameWithoutExtension(String filepath) {
        // Extract just the filename part (after the last separator)
        int lastSeparator = Math.max(filepath.lastIndexOf('/'), filepath.lastIndexOf('\\'));
        String filename = lastSeparator >= 0 ? filepath.substring(lastSeparator + 1) : filepath;
        // Remove extension
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
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
        creationSection = new JPanel(new GridBagLayout());
        JPanel p = creationSection;
        p.setBorder(BorderFactory.createTitledBorder(Messages.get("section.unitCreation")));

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
        cc.gridx = 0;
        cc.gridwidth = 2;
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

        cc.gridy = row++;
        p.add(flattenPathsBox, cc);

        addRow(p, lc, fc, row++, unitScalingLabel, unitScalingSpinner);

        return p;
    }

    private JPanel buildPlacementSection() {
        placementSection = new JPanel(new GridBagLayout());
        JPanel p = placementSection;
        p.setBorder(BorderFactory.createTitledBorder(Messages.get("section.unitPlacement")));

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
        addRow(p, lc, fc, row++, unitOriginIdLabel, unitOriginField);
        addRow(p, lc, fc, row++, unitAngleLabel, unitAngleSpinner);
        addRow(p, lc, fc, row++, spacingXLabel, unitSpacingXSpinner);
        addRow(p, lc, fc, row++, spacingYLabel, unitSpacingYSpinner);
        addRow(p, lc, fc, row++, placingOrderLabel, placingOrderCombo);

        return p;
    }

    private JPanel buildNamingSection() {
        namingSection = new JPanel(new GridBagLayout());
        JPanel p = namingSection;
        p.setBorder(BorderFactory.createTitledBorder(Messages.get("section.unitNaming")));

        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0;
        cc.gridy = 0;
        cc.gridwidth = 2;
        cc.anchor = GridBagConstraints.WEST;
        cc.insets = new Insets(5, 10, 5, 10);
        p.add(autoNameUnitsBox, cc);

        cc.gridy = 1;
        p.add(autoAssignIconBox, cc);

        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(3, 10, 3, 5);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(3, 2, 3, 10);

        addRow(p, lc, fc, 2, nameFormatLabel, nameFormatCombo);

        previewBtn = new JButton(Messages.get("button.preview"));
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0;
        bc.gridy = 3;
        bc.gridwidth = 2;
        bc.anchor = GridBagConstraints.CENTER;
        bc.insets = new Insets(8, 10, 5, 10);
        previewBtn.addActionListener(e -> showNamingPreview());
        p.add(previewBtn, bc);

        return p;
    }

    private void showNamingPreview() {
        if (selectedMdxFilenames.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Messages.get("preview.noMdxSelected"),
                    Messages.get("preview.namingTitle"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String format = getNameFormat();
        String[] columnNames = {Messages.get("preview.colOriginal"), Messages.get("preview.colFormatted")};
        Object[][] data = new Object[selectedMdxFilenames.size()][2];

        for (int i = 0; i < selectedMdxFilenames.size(); i++) {
            String filepath = selectedMdxFilenames.get(i);
            // Extract just the filename (no path, no extension)
            String filenameOnly = extractFilenameWithoutExtension(filepath);
            // Format the filename
            String formatted = NameFormatter.format(filenameOnly, format);
            data[i][0] = filenameOnly;
            data[i][1] = formatted;
        }

        JTable table = new JTable(data, columnNames);
        table.setDefaultEditor(Object.class, null);  // Make read-only
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(600, Math.min(300, selectedMdxFilenames.size() * 24 + 30)));

        JOptionPane.showMessageDialog(this,
                scrollPane,
                Messages.get("preview.namingTitle") + " — " + format,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel buildMapPanel() {
        JPanel p = new JPanel(new BorderLayout());

        // ---- Output path row (full width above the map image) ----
        browseBtn = new JButton(Messages.get("button.browse"));
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            String current = outputPathField.getText().trim();
            if (!current.isEmpty()) chooser.setSelectedFile(new java.io.File(current));
            chooser.setDialogTitle(Messages.get("dialog.saveOutput"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                outputPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        outputLabel = new JLabel(Messages.get("label.output") + " ");
        JPanel outputRow = new JPanel(new BorderLayout(5, 0));
        outputRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));
        outputRow.add(outputLabel, BorderLayout.WEST);
        outputRow.add(outputPathField, BorderLayout.CENTER);
        outputRow.add(browseBtn, BorderLayout.EAST);

        // ---- Toolbar (Clear button) ----
        clearBtn = new JButton(Messages.get("button.clear"));
        clearBtn.addActionListener(e -> mapPreviewPanel.clearShape());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(clearBtn);

        // Output row + toolbar stacked at the top
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(outputRow, BorderLayout.NORTH);
        topArea.add(toolbar, BorderLayout.SOUTH);

        // ---- Capacity status bar ----
        JPanel statusBar = new JPanel(new BorderLayout(5, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel statusLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusLeft.add(capacityIndicator);
        statusLeft.add(capacityLabel);

        statusBar.add(statusLeft, BorderLayout.CENTER);
        statusBar.add(helpButton, BorderLayout.EAST);

        p.add(topArea, BorderLayout.NORTH);
        p.add(mapPreviewPanel, BorderLayout.CENTER);
        p.add(statusBar, BorderLayout.SOUTH);
        return p;
    }

    // -------------------------------------------------------------------------
    // Capacity status
    // -------------------------------------------------------------------------

    /**
     * Updates the capacity indicator label and color based on current placement info.
     *
     * @param placed   number of unit positions rendered
     * @param total    number of MDX files selected (desired units)
     * @param capacity total grid slots available in the drawn shape
     */
    private void updateCapacityStatus(int placed, int total, int capacity) {
        if (total == 0) {
            capacityLabel.setText(Messages.get("status.noMdxSelected"));
            updateIndicatorColor(Color.GRAY);
        } else if (capacity == 0) {
            capacityLabel.setText(Messages.get("status.drawShape"));
            updateIndicatorColor(Color.GRAY);
        } else if (capacity >= total) {
            capacityLabel.setText(java.text.MessageFormat.format(
                    Messages.get("status.capacity"), total, total, capacity));
            updateIndicatorColor(new Color(60, 180, 75));  // green
        } else {
            capacityLabel.setText(java.text.MessageFormat.format(
                    Messages.get("status.capacityOver"), capacity, total, capacity));
            updateIndicatorColor(new Color(220, 50, 50));  // red
        }
    }

    private void updateIndicatorColor(Color color) {
        capacityIndicator.setIcon(new CircleIcon(12, color));
    }

    private void showCapacityHelp() {
        JOptionPane.showMessageDialog(this,
                Messages.get("help.capacityText"),
                Messages.get("help.capacityTitle"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------------------------------------------------------------
    // Config sync
    // -------------------------------------------------------------------------

    private void syncConfig() {
        mapPreviewPanel.setUnitAngle(((Number) unitAngleSpinner.getValue()).doubleValue());
        mapPreviewPanel.setUnitSpacingX(((Number) unitSpacingXSpinner.getValue()).doubleValue());
        mapPreviewPanel.setUnitSpacingY(((Number) unitSpacingYSpinner.getValue()).doubleValue());
        mapPreviewPanel.setShowTriangles(true);
        mapPreviewPanel.setPlacingOrder((MapPreviewPanel.PlacingOrder) placingOrderCombo.getSelectedItem());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the currently entered output file path.
     */
    public String getOutputPath() {
        return outputPathField.getText().trim();
    }

    /**
     * Called by MainFrame when a map is opened to pre-fill the output path.
     */
    public void setOutputPath(String path) {
        outputPathField.setText(path != null ? path : "");
    }

    /**
     * Called by MainFrame when a map is opened.
     */
    public void setMapPreviewImage(BufferedImage image) {
        mapPreviewPanel.setMapImage(image);
    }

    /**
     * Passes existing unit placements (from {@code war3mapUnits.doo}) to the map
     * preview so they are drawn as yellow triangles over the map image.
     */
    public void setExistingUnits(List<ExistingUnit> units) {
        mapPreviewPanel.setExistingUnits(units);
    }

    /**
     * Sets the list of selected MDX filenames for the naming preview
     * and updates the total unit count on the map preview.
     */
    public void setSelectedMdxFilenames(java.util.List<String> filenames) {
        this.selectedMdxFilenames = filenames != null ? filenames : java.util.Collections.emptyList();
        long unitCount = this.selectedMdxFilenames.stream()
                .filter(f -> !isPortraitMdx(f))
                .count();
        mapPreviewPanel.setTotalUnits((int) unitCount);
    }

    /** Returns true for portrait MDX files (e.g. {@code Footman_Portrait.mdx}). */
    static boolean isPortraitMdx(String filepath) {
        return filepath.toLowerCase().endsWith("_portrait.mdx");
    }

    // ---- Unit Creation Getters ----
    public boolean isCreateUnitsSelected() {
        return createUnitsBox.isSelected();
    }

    public boolean isPlaceUnitsSelected() {
        return placeUnitsBox.isSelected();
    }

    public boolean isClearUnitsSelected() {
        return clearUnitsBox.isSelected();
    }

    public boolean isClearAssetsSelected() {
        return clearAssetsBox.isSelected();
    }

    public double getUnitScaling() {
        return ((Number) unitScalingSpinner.getValue()).doubleValue();
    }

    // ---- Unit Placement Getters ----
    public String getUnitOriginId() {
        return unitOriginField.getValue();
    }

    /**
     * Populates the Unit Origin ID suggestion list.
     * Called by {@code MainFrame} when a map file is opened.
     */
    public void setUnitSuggestions(List<UnitEntry> entries) {
        unitOriginField.setSuggestions(entries);
    }

    public Integer getUnitAngle() {
        return ((Number) unitAngleSpinner.getValue()).intValue();
    }

    public double getUnitSpacingX() {
        return ((Number) unitSpacingXSpinner.getValue()).doubleValue();
    }

    public double getUnitSpacingY() {
        return ((Number) unitSpacingYSpinner.getValue()).doubleValue();
    }

    public MapPreviewPanel.PlacingOrder getPlacingOrder() {
        return (MapPreviewPanel.PlacingOrder) placingOrderCombo.getSelectedItem();
    }

    // ---- Unit Naming Getters ----
    public boolean isAutoNameUnitsEnabled() {
        return autoNameUnitsBox.isSelected();
    }

    public String getNameFormat() {
        NameFormatItem item = (NameFormatItem) nameFormatCombo.getSelectedItem();
        return item != null ? item.formatKey() : "Space Separated (keep case)";
    }

    public boolean isAutoAssignIconEnabled() {
        return autoAssignIconBox.isSelected();
    }

    public boolean isFlattenPathsEnabled() {
        return flattenPathsBox.isSelected();
    }

    /**
     * Returns the world-space placement bounds for the import.
     *
     * <p>If the user has drawn a rectangle on the preview, that rectangle is used.
     * Otherwise the full map camera boundary is used as the placement area.
     *
     * <p>The spacing spinners are in <em>screen pixels relative to the displayed map image</em>
     * and are converted to WC3 world units using the camera-bounds extent and the current
     * image display size, so the grid density matches what the user sees on screen.
     *
     * @return placement bounds in world coordinates, or {@code null} if no camera bounds
     * are available yet (map not loaded)
     */
    public ImportOptions.PlacementBounds getPlacementBounds() {
        CameraBounds cb = CameraBounds.getInstance();
        if (!cb.isInitialized()) return null;

        // Use the drawn rectangle if present; fall back to the full map boundary [0,1]×[0,1].
        double[] normBounds = mapPreviewPanel.getShapeBoundsNormalized();
        if (normBounds == null) {
            normBounds = new double[]{0.0, 0.0, 1.0, 1.0};
        }

        java.awt.Rectangle imgRect = mapPreviewPanel.getDisplayedImageRect();
        if (imgRect == null || imgRect.width == 0 || imgRect.height == 0) return null;

        float tlX = cb.getTopLeft().getX().getVal();
        float tlY = cb.getTopLeft().getY().getVal();   // north (high Y in WC3)
        float brX = cb.getBottomRight().getX().getVal();
        float brY = cb.getBottomRight().getY().getVal(); // south (low Y in WC3)

        // Map normalised [0..1] coords to world coords.
        // normX=0 → tlX (west),  normX=1 → brX (east)
        // normY=0 → tlY (north), normY=1 → brY (south)
        float worldX1 = tlX + (float) (normBounds[0] * (brX - tlX)); // west edge
        float worldY1 = tlY + (float) (normBounds[1] * (brY - tlY)); // north edge (high Y)
        float worldX2 = tlX + (float) (normBounds[2] * (brX - tlX)); // east edge
        float worldY2 = tlY + (float) (normBounds[3] * (brY - tlY)); // south edge (low Y)

        // Convert spacing from screen pixels → world units.
        // 1 screen pixel on the displayed image = worldWidth / imageDisplayWidth world units.
        float screenSpacingX = ((Number) unitSpacingXSpinner.getValue()).floatValue();
        float screenSpacingY = ((Number) unitSpacingYSpinner.getValue()).floatValue();
        float worldSpacingX = screenSpacingX * (brX - tlX) / imgRect.width;
        float worldSpacingY = screenSpacingY * (tlY - brY) / imgRect.height; // tlY-brY > 0

        return new ImportOptions.PlacementBounds(
                Math.min(worldX1, worldX2),  // minX (west)
                worldY2,                      // minY (south — lower Y value)
                Math.max(worldX1, worldX2),  // maxX (east)
                worldY1,                      // maxY (north — higher Y value)
                Math.max(1f, worldSpacingX),
                Math.max(1f, worldSpacingY)
        );
    }

    // -------------------------------------------------------------------------
    // i18n refresh
    // -------------------------------------------------------------------------

    /** Refreshes all translatable labels after a locale change. */
    public void applyI18n() {
        // Checkboxes
        createUnitsBox.setText(Messages.get("checkbox.createUnits"));
        placeUnitsBox.setText(Messages.get("checkbox.placeUnits"));
        clearUnitsBox.setText(Messages.get("checkbox.clearUnits"));
        clearAssetsBox.setText(Messages.get("checkbox.clearAssets"));
        flattenPathsBox.setText(Messages.get("checkbox.flattenPaths"));
        autoNameUnitsBox.setText(Messages.get("checkbox.autoNameUnits"));
        autoAssignIconBox.setText(Messages.get("checkbox.autoAssignIcon"));
        // Section titled borders — setBorder() triggers revalidate+repaint on each panel
        creationSection.setBorder(BorderFactory.createTitledBorder(Messages.get("section.unitCreation")));
        placementSection.setBorder(BorderFactory.createTitledBorder(Messages.get("section.unitPlacement")));
        namingSection.setBorder(BorderFactory.createTitledBorder(Messages.get("section.unitNaming")));
        // Combo boxes re-render their items via toString()
        nameFormatCombo.repaint();
        placingOrderCombo.repaint();
        // Row labels
        unitScalingLabel.setText(Messages.get("label.unitScaling"));
        unitOriginIdLabel.setText(Messages.get("label.unitOriginId"));
        unitAngleLabel.setText(Messages.get("label.unitAngle"));
        spacingXLabel.setText(Messages.get("label.spacingX"));
        spacingYLabel.setText(Messages.get("label.spacingY"));
        placingOrderLabel.setText(Messages.get("label.placingOrder"));
        nameFormatLabel.setText(Messages.get("label.nameFormat"));
        outputLabel.setText(Messages.get("label.output") + " ");
        // Sub-panel buttons
        previewBtn.setText(Messages.get("button.preview"));
        clearBtn.setText(Messages.get("button.clear"));
        browseBtn.setText(Messages.get("button.browse"));
        // Tooltips
        createUnitsBox.setToolTipText(Messages.get("tooltip.createUnits"));
        placeUnitsBox.setToolTipText(Messages.get("tooltip.placeUnits"));
        clearUnitsBox.setToolTipText(Messages.get("tooltip.clearUnits"));
        clearAssetsBox.setToolTipText(Messages.get("tooltip.clearAssets"));
        flattenPathsBox.setToolTipText(Messages.get("tooltip.flattenPaths"));
        unitScalingSpinner.setToolTipText(Messages.get("tooltip.unitScaling"));
        unitOriginField.setToolTipText(Messages.get("tooltip.unitOriginId"));
        unitAngleSpinner.setToolTipText(Messages.get("tooltip.unitAngle"));
        unitSpacingXSpinner.setToolTipText(Messages.get("tooltip.spacingX"));
        unitSpacingYSpinner.setToolTipText(Messages.get("tooltip.spacingY"));
        placingOrderCombo.setToolTipText(Messages.get("tooltip.placingOrder"));
        autoNameUnitsBox.setToolTipText(Messages.get("tooltip.autoNameUnits"));
        autoAssignIconBox.setToolTipText(Messages.get("tooltip.autoAssignIcon"));
        nameFormatCombo.setToolTipText(Messages.get("tooltip.nameFormat"));
        outputPathField.setToolTipText(Messages.get("tooltip.outputPath"));
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** Pairs an internal format key with its i18n message key. */
    private record NameFormatItem(String formatKey, String i18nKey) {
        @Override public String toString() { return Messages.get(i18nKey); }
    }

    /**
     * Small icon that paints a filled circle with the given color.
     */
    private static class CircleIcon implements Icon {
        private final int size;
        private final Color color;

        CircleIcon(int size, Color color) {
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y, size, size);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
