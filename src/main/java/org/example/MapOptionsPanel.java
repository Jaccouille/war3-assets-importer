package org.example;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class MapOptionsPanel extends JPanel {
    // Labels
    private final JTextField nameField = new JTextField();
    private final JTextField descField = new JTextField();
    private final JTextField authorField = new JTextField();
    private final JTextField mapVersionField = new JTextField();
    private final JTextField editorVersionField = new JTextField();


    // Checkboxes
    private final JCheckBox createUnitsBox = new JCheckBox("Create units");
    private final JCheckBox placeUnitsBox = new JCheckBox("Place units on terrain");
    private final JCheckBox clearUnitsBox = new JCheckBox("Clear all units");
    private final JCheckBox clearAssetsBox = new JCheckBox("Clear existing assets");

    // Unit definition dropdown
    private final JComboBox<String> unitDefinitionSelect = new JComboBox<>();
    private final JLabel imageLabel = new JLabel();
    private final int previewSize = 128;
    private ImageIcon currentImage;

    public MapOptionsPanel() {
        setLayout(new BorderLayout());

        imageLabel.setPreferredSize(new Dimension(previewSize, previewSize));
        imageLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        imageLabel.setVerticalAlignment(SwingConstants.TOP);
        imageLabel.setIcon(generateFallbackImage());

        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.add(imageLabel, BorderLayout.NORTH);

        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));
        labelPanel.add(createLabelFieldRow("Name:", nameField));
        labelPanel.add(createLabelFieldRow("Author:", authorField));
        labelPanel.add(createLabelFieldRow("Game Version:", mapVersionField));
        labelPanel.add(createLabelFieldRow("Editor Version:", editorVersionField));
        labelPanel.add(createLabelFieldRow("Description:", descField));
        for (Component c : labelPanel.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
            }
        }

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(labelPanel, BorderLayout.CENTER);
        headerPanel.add(imageLabel, BorderLayout.EAST);
        labelPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        imageLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel checkboxGrid = new JPanel(new GridLayout(0, 2, 10, 5)); // 0 rows = auto
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
        formPanel.add(new JLabel("Unit Definition:"));
        formPanel.add(unitDefinitionSelect);
        unitDefinitionSelect.setVisible(false); // hidden by default

        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // top, left, bottom, right
        add(formPanel, BorderLayout.CENTER);

        // Toggle dropdown visibility based on checkbox
        createUnitsBox.addActionListener(e -> unitDefinitionSelect.setVisible(createUnitsBox.isSelected()));
    }

    // Setters for map info
    public void setMapName(String name) {
        nameField.setText(name);
    }
    public void setDescription(String desc) {
        descField.setText(desc);
    }
    public void setAuthor(String author) {
        authorField.setText(author);
    }
    public void setMapVersion(String version) {
        mapVersionField.setText(version);
    }
    public void setEditorVersion(String version) {
        editorVersionField.setText(version);
    }


    // For now, add unit options as strings (will be UnitDefinition later)
    public void setUnitDefinitions(java.util.List<String> unitNames) {
        unitDefinitionSelect.removeAllItems();
        for (String name : unitNames) {
            unitDefinitionSelect.addItem(name);
        }
    }

    // Checkbox accessors
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

    public String getSelectedUnitDefinition() {
        return (String) unitDefinitionSelect.getSelectedItem();
    }
    public void setPreviewImage(byte[] imageBytes) {
        try {
            if (imageBytes != null && imageBytes.length > 0) {
                ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                BufferedImage image = ImageIO.read(bais);
                if (image != null) {
                    Image scaled = image.getScaledInstance(previewSize, previewSize, Image.SCALE_SMOOTH);
                    currentImage = new ImageIcon(scaled);
                } else {
                    currentImage = generateFallbackImage();
                }
            } else {
                currentImage = generateFallbackImage();
            }
        } catch (IOException e) {
            e.printStackTrace();
            currentImage = generateFallbackImage();
        }
        imageLabel.setIcon(currentImage);
    }




    private ImageIcon generateFallbackImage() {
        int size = previewSize;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Fill background
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, size, size);

        // Draw cross
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.drawLine(-1, -1, size, size);     // Top-left to bottom-right
        g.drawLine(size, -1, -1, size);     // Top-right to bottom-left

        g.dispose();
        return new ImageIcon(img);
    }

    private JPanel createLabelFieldRow(String labelText, JTextField field) {
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD));

        field.setEditable(false);
        field.setBorder(null);
        field.setOpaque(false);
        field.setFocusable(false);
        field.setDisabledTextColor(Color.BLACK);
        field.setFont(field.getFont().deriveFont(Font.PLAIN));

        // Fix height to match label height
        Dimension prefSize = new Dimension(Short.MAX_VALUE, label.getPreferredSize().height);
        field.setMaximumSize(prefSize);
        field.setPreferredSize(prefSize);
        field.setMinimumSize(prefSize);

        JPanel rowPanel = new JPanel(new BorderLayout(5, 0));
        rowPanel.add(label, BorderLayout.WEST);
        rowPanel.add(field, BorderLayout.CENTER);
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return rowPanel;
    }


}
