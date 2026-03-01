package com.hiveworkshop.war3assetsimporter.gui.settings;

import com.hiveworkshop.war3assetsimporter.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;

/**
 * Settings panel for selecting the application Look & Feel.
 *
 * <p>The combo box is populated dynamically from {@link UIManager#getInstalledLookAndFeels()},
 * so it shows whatever L&Fs are available on the current JVM/platform (typically 4–5 options).
 * Selecting an entry applies it immediately and fires the optional
 * {@link LookAndFeelChangeListener} so parent windows can refresh their UI trees.
 */
public class LookAndFeelPanel extends JPanel {

    private final AppearanceConfig config;
    private final JComboBox<LnFItem> combo;
    private LookAndFeelChangeListener changeListener;
    public LookAndFeelPanel(AppearanceConfig config) {
        this.config = config;
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(new JLabel(Messages.get("settings.laf.label")));

        // Populate from the platform's installed L&Fs
        UIManager.LookAndFeelInfo[] infos = UIManager.getInstalledLookAndFeels();
        LnFItem[] items = new LnFItem[infos.length];
        for (int i = 0; i < infos.length; i++) {
            items[i] = new LnFItem(infos[i]);
        }

        combo = new JComboBox<>(items);

        // Pre-select the currently active L&F
        String currentClass = UIManager.getLookAndFeel().getClass().getName();
        for (LnFItem item : items) {
            if (item.info.getClassName().equals(currentClass)) {
                combo.setSelectedItem(item);
                break;
            }
        }

        combo.addActionListener(e -> apply());

        add(combo);

        JLabel note = new JLabel(Messages.get("settings.laf.note"));
        note.setForeground(Color.GRAY);
        note.setFont(note.getFont().deriveFont(Font.ITALIC, 11f));
        add(note);
    }

    /**
     * Registers a callback invoked right after a new L&F has been applied.
     * Typically used to call {@code SwingUtilities.updateComponentTreeUI} on
     * all open windows.
     */
    public void setLookAndFeelChangeListener(LookAndFeelChangeListener listener) {
        this.changeListener = listener;
    }

    private void apply() {
        LnFItem selected = (LnFItem) combo.getSelectedItem();
        if (selected == null) return;

        String className = selected.info.getClassName();
        if (className.equals(UIManager.getLookAndFeel().getClass().getName())) return;

        try {
            UIManager.setLookAndFeel(className);
            config.setLookAndFeelClassName(className);
            if (changeListener != null) changeListener.onLookAndFeelChanged();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    Messages.get("settings.laf.error") + "\n" + ex.getMessage(),
                    Messages.get("status.error"),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Callback fired after the L&F has been switched.
     */
    public interface LookAndFeelChangeListener {
        void onLookAndFeelChanged();
    }

    // -------------------------------------------------------------------------
    // Inner wrapper
    // -------------------------------------------------------------------------

    /**
     * Wraps a {@link UIManager.LookAndFeelInfo} for display in the combo box.
     */
    private static class LnFItem {
        final UIManager.LookAndFeelInfo info;

        LnFItem(UIManager.LookAndFeelInfo info) {
            this.info = info;
        }

        @Override
        public String toString() {
            return info.getName();
        }
    }
}
