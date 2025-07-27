package org.example;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class PreviewPanel extends JPanel {
    private final JLabel imageLabel = new JLabel();
    private final int previewSize = 256; // or any preferred default size

    public PreviewPanel() {
        setLayout(new GridBagLayout()); // centers the imageLabel
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        add(imageLabel);
        setBorder(BorderFactory.createTitledBorder("Preview"));
        setPreferredSize(new Dimension(previewSize + 40, previewSize + 40));
    }

    public void setImage(BufferedImage image) {
        if (image != null) {
            Image scaled = image.getScaledInstance(previewSize, previewSize, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaled));
        } else {
            imageLabel.setIcon(generateFallbackImage());
        }
    }

    public void setImage(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            setImage(image);
        } catch (IOException e) {
            e.printStackTrace();
            imageLabel.setIcon(generateFallbackImage());
        }
    }

    public void setImage(byte[] imageData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            setImage(image);
        } catch (IOException e) {
            e.printStackTrace();
            imageLabel.setIcon(generateFallbackImage());
        }
    }

    private ImageIcon generateFallbackImage() {
        int size = previewSize;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, size, size);

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.drawLine(0, 0, size, size);
        g.drawLine(size, 0, 0, size);

        g.dispose();
        return new ImageIcon(img);
    }
}
