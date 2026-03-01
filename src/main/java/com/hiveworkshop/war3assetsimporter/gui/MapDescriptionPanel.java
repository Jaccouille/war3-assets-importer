package com.hiveworkshop.war3assetsimporter.gui;

import com.hiveworkshop.war3assetsimporter.gui.i18n.Messages;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Left-hand panel showing map metadata (read-only) and processing options (user-editable).
 * All labels are sourced from the {@link Messages} bundle for i18n support.
 */
public class MapDescriptionPanel extends JPanel {

    private static final int PREVIEW_SIZE = 128;
    // ---- Read-only metadata fields ----
    private final JTextField nameField = new JTextField();
    private final JTextArea descArea = new JTextArea();
    private final JTextField authorField = new JTextField();
    private final JTextField mapVersionField = new JTextField();
    private final JTextField editorVersionField = new JTextField();
    // ---- Preview image ----
    private final JLabel imageLabel = new JLabel();
    private ImageIcon currentImage;

    // ---- Label references for i18n refresh ----
    private JLabel nameLabel, authorLabel, gameVersionLabel, editorVersionLabel, descLabel;

    public MapDescriptionPanel() {
        setLayout(new BorderLayout());
        imageLabel.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imageLabel.setIcon(generateFallbackImage());

        buildLayout();
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

        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));
        labelPanel.add(createRow(nameLabel, nameField));
        labelPanel.add(Box.createVerticalStrut(5));
        labelPanel.add(createRow(authorLabel, authorField));
        labelPanel.add(Box.createVerticalStrut(5));
        labelPanel.add(createRow(gameVersionLabel, mapVersionField));
        labelPanel.add(Box.createVerticalStrut(5));
        labelPanel.add(createRow(editorVersionLabel, editorVersionField));
        // Cap single-line rows and struts to their preferred heights
        for (Component c : labelPanel.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
            }
        }
        // Description row added after the cap loop so it can grow vertically
        labelPanel.add(Box.createVerticalStrut(5));
        labelPanel.add(createDescRow(descLabel));
        labelPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(labelPanel, BorderLayout.CENTER);
        headerPanel.add(imageLabel, BorderLayout.EAST);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.add(headerPanel);
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(formPanel, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // i18n
    // -------------------------------------------------------------------------

    /**
     * Refreshes all translatable labels. Called when the locale changes.
     */
    public void applyI18n() {
        nameLabel.setText(Messages.get("label.name"));
        authorLabel.setText(Messages.get("label.author"));
        gameVersionLabel.setText(Messages.get("label.gameVersion"));
        editorVersionLabel.setText(Messages.get("label.editorVersion"));
        descLabel.setText(Messages.get("label.description"));
        repaint();
    }

    // -------------------------------------------------------------------------
    // Metadata setters
    // -------------------------------------------------------------------------

    public void setMapName(String name) {
        nameField.setText(name);
    }

    public void setDescription(String desc) {
        descArea.setText(desc);
        descArea.setCaretPosition(0);
    }

    public void setAuthor(String author) {
        authorField.setText(author);
    }

    public void setMapVersion(String version) {
        mapVersionField.setText(version);
    }

    public void setEditorVersion(String v) {
        editorVersionField.setText(v);
    }

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
        field.setEditable(false);  // Read-only: cannot edit
        field.setBorder(null);
        field.setOpaque(false);
        field.setFocusable(true);  // Allow focus for text selection
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

    /**
     * Builds the Description row with a multi-line, read-only, word-wrapping JTextArea.
     */
    private JPanel createDescRow(JLabel label) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setVerticalAlignment(SwingConstants.TOP);

        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setOpaque(false);
        descArea.setFont(nameField.getFont().deriveFont(Font.PLAIN));
        descArea.setRows(3);

        JScrollPane scroll = new JScrollPane(descArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);

        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.add(label, BorderLayout.WEST);
        row.add(scroll, BorderLayout.CENTER);
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
