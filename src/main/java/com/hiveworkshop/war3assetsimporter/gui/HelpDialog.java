package com.hiveworkshop.war3assetsimporter.gui;

import com.hiveworkshop.war3assetsimporter.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Modal help dialog that documents the full workflow and every configuration
 * option available in the application.
 */
public class HelpDialog extends JDialog {

    public HelpDialog(Frame owner) {
        super(owner, Messages.get("help.title"), true);
        setSize(720, 580);
        setLocationRelativeTo(owner);
        setResizable(true);

        JEditorPane editorPane = new JEditorPane("text/html", loadHtml());
        editorPane.setEditable(false);
        editorPane.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(editorPane);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JButton closeBtn = new JButton(Messages.get("button.close"));
        closeBtn.addActionListener(e -> dispose());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeBtn);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        // Close on Escape
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(closeBtn);
    }

    /**
     * Loads the help HTML from a locale-specific classpath resource.
     * Falls back to {@code help_en.html} if the current locale has no dedicated file.
     */
    private static String loadHtml() {
        String lang = Messages.getLocale().getLanguage();
        String resource = "/help_" + lang + ".html";
        InputStream is = HelpDialog.class.getResourceAsStream(resource);
        if (is == null) {
            is = HelpDialog.class.getResourceAsStream("/help_en.html");
        }
        if (is == null) return "<html><body>Help unavailable.</body></html>";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "<html><body>Help unavailable.</body></html>";
        }
    }
}
