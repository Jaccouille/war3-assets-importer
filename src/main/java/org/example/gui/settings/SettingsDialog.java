package org.example.gui.settings;

import org.example.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Modal settings dialog with two tabs:
 * <ol>
 *   <li><b>Language</b> — locale switcher; triggers live UI refresh via callback</li>
 *   <li><b>Keybindings</b> — hotkey table; persisted on dialog close</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   SettingsDialog dlg = new SettingsDialog(parentFrame, config);
 *   dlg.setLocaleChangeListener(locale -> mainFrame.applyI18n());
 *   dlg.setVisible(true);
 * </pre>
 */
public class SettingsDialog extends JDialog {

    private final KeybindingsConfig config;
    private final LanguagePanel languagePanel;
    private final KeybindingPanel keybindingPanel;

    public SettingsDialog(JFrame parent, KeybindingsConfig config) {
        super(parent, Messages.get("settings.title"), true /* modal */);
        this.config = config;

        languagePanel = new LanguagePanel();
        keybindingPanel = new KeybindingPanel(config);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.get("settings.tab.language"), languagePanel);
        tabs.addTab(Messages.get("settings.tab.keybindings"), keybindingPanel);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> onClose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(closeBtn);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setSize(500, 380);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Registers a callback that fires immediately when the user switches locale.
     * Typically used to call {@code mainFrame.applyI18n()}.
     */
    public void setLocaleChangeListener(LanguagePanel.LocaleChangeListener listener) {
        languagePanel.setLocaleChangeListener(locale -> {
            // Refresh dialog's own labels first
            applyI18n();
            // Then notify the parent
            listener.onLocaleChanged(locale);
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void onClose() {
        config.save();
        dispose();
    }

    /** Refreshes all labels in the dialog after a locale switch. */
    public void applyI18n() {
        setTitle(Messages.get("settings.title"));
        // Re-title tabs — find them by index
        JTabbedPane tabs = (JTabbedPane) ((BorderLayout) getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        if (tabs != null) {
            tabs.setTitleAt(0, Messages.get("settings.tab.language"));
            tabs.setTitleAt(1, Messages.get("settings.tab.keybindings"));
        }
        languagePanel.applyI18n();
        keybindingPanel.applyI18n();
        repaint();
    }
}
