package org.example.gui;

import org.example.gui.i18n.Messages;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Left-hand panel showing map metadata (read-only) and processing options (user-editable).
 * All labels are sourced from the {@link Messages} bundle for i18n support.
 */
public class MapOptionsPanel extends JPanel {

    // ---- Read-only metadata fields ----
    private final JTextField nameField = new JTextField();
    private final JTextField descField = new JTextField();
    private final JTextField authorField = new JTextField();
    private final JTextField mapVersionField = new JTextField();
    private final JTextField editorVersionField = new JTextField();

    // ---- Processing option checkboxes ----
    private final JCheckBox createUnitsBox = new JCheckBox();
    private final JCheckBox placeUnitsBox = new JCheckBox();
    private final JCheckBox clearUnitsBox = new JCheckBox();
    private final JCheckBox clearAssetsBox = new JCheckBox();

    // ---- Unit definition dropdown ----
    private final JComboBox<String> unitDefinitionSelect = new JComboBox<>();

    // ---- Preview image ----
    private final JLabel imageLabel = new JLabel();
    private static final int PREVIEW_SIZE = 128;
    private ImageIcon currentImage;

    // ---- Label references for i18n refresh ----
    private JLabel nameLabel, authorLabel, gameVersionLabel, editorVersionLabel, descLabel, unitDefLabel;

    public MapOptionsPanel() {
        setLayout(new BorderLayout());
        imageLabel.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imageLabel.setIcon(generateFallbackImage());

        buildLayout();
        applyCheckboxLabels();

        createUnitsBox.addActionListener(e -> unitDefinitionSelect.setVisible(createUnitsBox.isSelected()));
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private void buildLayout() {
        nameLabel = new JLabel(Messages.get("label.name"));
        authorLabel = new JLabel(Messages.get("label.author"));
        gameVersionLabel = new JLabel(Messages.get("label.gameVersion"));
        editorVersionLabel = new JLabel(Messages.get("label.editorVersion"));
        descLabel = new JLabel(Messages.get("label.description"));
        unitDefLabel = new JLabel(Messages.get("label.unitDefinition"));

        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));
        labelPanel.add(createRow(nameLabel, nameField));
        labelPanel.add(createRow(authorLabel, authorField));
        labelPanel.add(createRow(gameVersionLabel, mapVersionField));
        labelPanel.add(createRow(editorVersionLabel, editorVersionField));
        labelPanel.add(createRow(descLabel, descField));
        for (Component c : labelPanel.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
            }
        }
        labelPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(labelPanel, BorderLayout.CENTER);
        headerPanel.add(imageLabel, BorderLayout.EAST);

        JPanel checkboxGrid = new JPanel(new GridLayout(0, 2, 10, 5));
        checkboxGrid.add(createUnitsBox);
        checkboxGrid.add(placeUnitsBox);
        checkboxGrid.add(clearUnitsBox);
        checkboxGrid.add(clearAssetsBox);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.add(headerPanel);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(checkboxGrid);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(unitDefLabel);
        formPanel.add(unitDefinitionSelect);
        unitDefinitionSelect.setVisible(false);
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(formPanel, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // i18n
    // -------------------------------------------------------------------------

    /** Refreshes all translatable labels. Called when the locale changes. */
    public void applyI18n() {
        nameLabel.setText(Messages.get("label.name"));
        authorLabel.setText(Messages.get("label.author"));
        gameVersionLabel.setText(Messages.get("label.gameVersion"));
        editorVersionLabel.setText(Messages.get("label.editorVersion"));
        descLabel.setText(Messages.get("label.description"));
        unitDefLabel.setText(Messages.get("label.unitDefinition"));
        applyCheckboxLabels();
        repaint();
    }

    private void applyCheckboxLabels() {
        createUnitsBox.setText(Messages.get("checkbox.createUnits"));
        placeUnitsBox.setText(Messages.get("checkbox.placeUnits"));
        clearUnitsBox.setText(Messages.get("checkbox.clearUnits"));
        clearAssetsBox.setText(Messages.get("checkbox.clearAssets"));
    }

    // -------------------------------------------------------------------------
    // Metadata setters
    // -------------------------------------------------------------------------

    public void setMapName(String name)       { nameField.setText(name); }
    public void setDescription(String desc)   { descField.setText(desc); }
    public void setAuthor(String author)      { authorField.setText(author); }
    public void setMapVersion(String version) { mapVersionField.setText(version); }
    public void setEditorVersion(String v)    { editorVersionField.setText(v); }

    public void setUnitDefinitions(List<String> unitNames) {
        unitDefinitionSelect.removeAllItems();
        for (String name : unitNames) unitDefinitionSelect.addItem(name);
    }

    // -------------------------------------------------------------------------
    // Option accessors
    // -------------------------------------------------------------------------

    public boolean isCreateUnitsSelected()  { return createUnitsBox.isSelected(); }
    public boolean isPlaceUnitsSelected()   { return placeUnitsBox.isSelected(); }
    public boolean isClearUnitsSelected()   { return clearUnitsBox.isSelected(); }
    public boolean isClearAssetsSelected()  { return clearAssetsBox.isSelected(); }
    public String  getSelectedUnitDefinition() { return (String) unitDefinitionSelect.getSelectedItem(); }

    // -------------------------------------------------------------------------
    // Preview image
    // -------------------------------------------------------------------------

    public void setPreviewImage(byte[] imageBytes) {
        try {
            if (imageBytes != null && imageBytes.length > 0) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (image != null) {
                    Image scaled = image.getScaledInstance(PREVIEW_SIZE, PREVIEW_SIZE, Image.SCALE_SMOOTH);
                    currentImage = new ImageIcon(scaled);
                } else {
                    currentImage = generateFallbackImage();
                }
            } else {
                currentImage = generateFallbackImage();
            }
        } catch (IOException e) {
            currentImage = generateFallbackImage();
        }
        imageLabel.setIcon(currentImage);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private JPanel createRow(JLabel label, JTextField field) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        field.setEditable(false);
        field.setBorder(null);
        field.setOpaque(false);
        field.setFocusable(false);
        field.setDisabledTextColor(Color.BLACK);
        field.setFont(field.getFont().deriveFont(Font.PLAIN));
        Dimension pref = new Dimension(Short.MAX_VALUE, label.getPreferredSize().height);
        field.setMaximumSize(pref);
        field.setPreferredSize(pref);
        field.setMinimumSize(pref);

        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private ImageIcon generateFallbackImage() {
        int s = PREVIEW_SIZE;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, s, s);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.drawLine(-1, -1, s, s);
        g.drawLine(s, -1, -1, s);
        g.dispose();
        return new ImageIcon(img);
    }
}
