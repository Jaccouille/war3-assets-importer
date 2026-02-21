package org.example.gui;

import org.example.gui.i18n.Messages;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Panel that displays a scaled preview of the selected asset (BLP texture or MDX placeholder).
 */
public class PreviewPanel extends JPanel {

    private final JLabel imageLabel = new JLabel();
    private static final int PREVIEW_SIZE = 256;

    public PreviewPanel() {
        setLayout(new GridBagLayout());
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        add(imageLabel);
        applyI18n();
        setPreferredSize(new Dimension(PREVIEW_SIZE + 40, PREVIEW_SIZE + 40));
    }

    // -------------------------------------------------------------------------
    // Image setters
    // -------------------------------------------------------------------------

    public void setImage(BufferedImage image) {
        if (image != null) {
            Image scaled = image.getScaledInstance(PREVIEW_SIZE, PREVIEW_SIZE, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaled));
        } else {
            imageLabel.setIcon(generateFallbackImage());
        }
    }

    public void setImage(File file) {
        try {
            setImage(ImageIO.read(file));
        } catch (IOException e) {
            imageLabel.setIcon(generateFallbackImage());
        }
    }

    public void setImage(byte[] imageData) {
        try {
            setImage(ImageIO.read(new ByteArrayInputStream(imageData)));
        } catch (IOException e) {
            imageLabel.setIcon(generateFallbackImage());
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
    // Fallback image
    // -------------------------------------------------------------------------

    private ImageIcon generateFallbackImage() {
        int s = PREVIEW_SIZE;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, s, s);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.drawLine(0, 0, s, s);
        g.drawLine(s, 0, 0, s);
        g.dispose();
        return new ImageIcon(img);
    }
}
