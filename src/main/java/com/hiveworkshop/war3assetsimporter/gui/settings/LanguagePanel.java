package com.hiveworkshop.war3assetsimporter.gui.settings;

import com.hiveworkshop.war3assetsimporter.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Locale;

/**
 * Settings panel for selecting the application language.
 *
 * <p>Selecting a locale immediately calls {@link Messages#setLocale(Locale)} and fires
 * the optional {@link LocaleChangeListener} so the parent window can refresh all labels.
 */
public class LanguagePanel extends JPanel {

    /**
     * Supported locales in display order.
     */
    private static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, Locale.FRENCH);
    private final JComboBox<LocaleItem> localeCombo;
    private LocaleChangeListener changeListener;
    public LanguagePanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(new JLabel(Messages.get("settings.language.label")));

        LocaleItem[] items = SUPPORTED.stream()
                .map(LocaleItem::new)
                .toArray(LocaleItem[]::new);

        localeCombo = new JComboBox<>(items);

        // Pre-select the currently active locale
        Locale current = Messages.getLocale();
        for (LocaleItem item : items) {
            if (item.locale.getLanguage().equals(current.getLanguage())) {
                localeCombo.setSelectedItem(item);
                break;
            }
        }

        localeCombo.addActionListener(e -> {
            LocaleItem selected = (LocaleItem) localeCombo.getSelectedItem();
            if (selected != null) {
                Messages.setLocale(selected.locale);
                if (changeListener != null) {
                    changeListener.onLocaleChanged(selected.locale);
                }
            }
        });

        add(localeCombo);

        JLabel note = new JLabel(Messages.get("settings.language.note"));
        note.setForeground(Color.GRAY);
        note.setFont(note.getFont().deriveFont(Font.ITALIC, 11f));
        add(note);
    }

    public void setLocaleChangeListener(LocaleChangeListener listener) {
        this.changeListener = listener;
    }

    /**
     * Refreshes labels after an external locale change.
     */
    public void applyI18n() {
        // Update the note label text — rebuild is simpler than keeping a reference
        removeAll();
        add(new JLabel(Messages.get("settings.language.label")));
        add(localeCombo);
        JLabel note = new JLabel(Messages.get("settings.language.note"));
        note.setForeground(Color.GRAY);
        note.setFont(note.getFont().deriveFont(Font.ITALIC, 11f));
        add(note);
        revalidate();
        repaint();
    }

    /**
     * Callback fired when the user picks a new locale.
     */
    public interface LocaleChangeListener {
        void onLocaleChanged(Locale newLocale);
    }

    // -------------------------------------------------------------------------
    // Inner helpers
    // -------------------------------------------------------------------------

    /**
     * Wraps a Locale for display in the combo box.
     */
    private static class LocaleItem {
        final Locale locale;

        LocaleItem(Locale locale) {
            this.locale = locale;
        }

        @Override
        public String toString() {
            // Capitalize first letter for nicer display
            String name = locale.getDisplayLanguage(locale);
            if (name.isEmpty()) return locale.toString();
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }
}
