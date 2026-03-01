package com.hiveworkshop.war3assetsimporter.gui.settings;

import com.hiveworkshop.war3assetsimporter.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;

/**
 * Modal settings dialog with two tabs:
 * <ol>
 *   <li><b>Language</b> — locale switcher; triggers live UI refresh via callback</li>
 *   <li><b>Look & Feel</b> — Swing UI style selector; applied immediately</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   SettingsDialog dlg = new SettingsDialog(parentFrame, appearanceConfig);
 *   dlg.setLocaleChangeListener(locale -> mainFrame.applyI18n());
 *   dlg.setLookAndFeelChangeListener(() -> SwingUtilities.updateComponentTreeUI(mainFrame));
 *   dlg.setVisible(true);
 * </pre>
 */
public class SettingsDialog extends JDialog {

    private final AppearanceConfig appearanceConfig;
    private final LanguagePanel languagePanel;
    private final LookAndFeelPanel lookAndFeelPanel;
    private final JTabbedPane tabs;

    public SettingsDialog(JFrame parent, AppearanceConfig appearanceConfig) {
        super(parent, Messages.get("settings.title"), true /* modal */);
        this.appearanceConfig = appearanceConfig;

        languagePanel = new LanguagePanel();
        lookAndFeelPanel = new LookAndFeelPanel(appearanceConfig);

        tabs = new JTabbedPane();
        tabs.addTab(Messages.get("settings.tab.language"), languagePanel);
        tabs.addTab(Messages.get("settings.tab.lookAndFeel"), lookAndFeelPanel);

        JButton closeBtn = new JButton(Messages.get("button.close"));
        closeBtn.addActionListener(e -> onClose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(closeBtn);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setSize(500, 300);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Registers a callback that fires immediately when the user switches locale.
     * Typically used to call {@code mainFrame.applyI18n()}.
     */
    public void setLocaleChangeListener(LanguagePanel.LocaleChangeListener listener) {
        languagePanel.setLocaleChangeListener(locale -> {
            // Persist the chosen locale so it survives application restarts
            appearanceConfig.setLocaleLanguage(locale.getLanguage());
            // Refresh dialog's own labels first
            applyI18n();
            // Then notify the parent
            listener.onLocaleChanged(locale);
        });
    }

    /**
     * Registers a callback that fires immediately when the user switches Look & Feel.
     * Typically used to call {@code SwingUtilities.updateComponentTreeUI(mainFrame)}.
     */
    public void setLookAndFeelChangeListener(LookAndFeelPanel.LookAndFeelChangeListener listener) {
        lookAndFeelPanel.setLookAndFeelChangeListener(() -> {
            // Refresh dialog's own UI tree first
            SwingUtilities.updateComponentTreeUI(this);
            pack();
            // Then notify the parent (main frame)
            listener.onLookAndFeelChanged();
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void onClose() {
        appearanceConfig.save();
        dispose();
    }

    /**
     * Refreshes all labels in the dialog after a locale switch.
     */
    public void applyI18n() {
        setTitle(Messages.get("settings.title"));
        tabs.setTitleAt(0, Messages.get("settings.tab.language"));
        tabs.setTitleAt(1, Messages.get("settings.tab.lookAndFeel"));
        languagePanel.applyI18n();
        repaint();
    }
}
