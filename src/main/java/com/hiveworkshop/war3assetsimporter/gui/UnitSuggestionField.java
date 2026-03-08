package com.hiveworkshop.war3assetsimporter.gui;

import com.hiveworkshop.war3assetsimporter.core.model.UnitEntry;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A text field with an inline suggestion popup.
 *
 * <p>As the user types, the popup shows matching {@link UnitEntry} items filtered by
 * the current text (case-insensitive match on ID or display name).
 * Selecting an entry — via click or keyboard — copies the unit's 4-character ID into
 * the field and closes the popup.
 *
 * <p>The list of candidates is supplied via {@link #setSuggestions(List)} when a map
 * is opened.  The field remains freely editable even when no suggestions are loaded
 * (the user can always type a raw ID like {@code "hfoo"}).
 *
 * <ul>
 *   <li>{@code ↓ / ↑} — navigate the suggestion list</li>
 *   <li>{@code Enter} — accept the highlighted suggestion</li>
 *   <li>{@code Escape} — close the popup without changing the field</li>
 * </ul>
 */
public class UnitSuggestionField extends JPanel {

    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int ROW_HEIGHT = 22;
    private static final int MIN_POPUP_WIDTH = 180;

    private final JTextField field;
    private final JPopupMenu popup;
    private final JList<UnitEntry> suggestionList;
    private final DefaultListModel<UnitEntry> listModel;
    private final JScrollPane scrollPane;

    private List<UnitEntry> allEntries = Collections.emptyList();

    /**
     * Guard that prevents the DocumentListener from re-filtering while we set text programmatically.
     */
    private boolean suppressFilter = false;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public UnitSuggestionField(String defaultValue) {
        setLayout(new BorderLayout());

        field = new JTextField(defaultValue, 8);

        listModel = new DefaultListModel<>();
        suggestionList = new JList<>(listModel);
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setFocusable(false);         // keep focus on the text field
        suggestionList.setFixedCellHeight(ROW_HEIGHT);

        scrollPane = new JScrollPane(suggestionList);
        scrollPane.setBorder(null);

        popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(scrollPane, BorderLayout.CENTER);
        popup.setFocusable(false);                  // prevents stealing keyboard focus

        add(field, BorderLayout.CENTER);

        // --- Filter while typing ---
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onTextChanged();
            }
        });

        // --- Keyboard navigation inside the popup ---
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popup.isVisible()) return;
                int idx = suggestionList.getSelectedIndex();
                int size = listModel.getSize();
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    suggestionList.setSelectedIndex(Math.min(idx + 1, size - 1));
                    suggestionList.ensureIndexIsVisible(suggestionList.getSelectedIndex());
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    suggestionList.setSelectedIndex(Math.max(idx - 1, 0));
                    suggestionList.ensureIndexIsVisible(suggestionList.getSelectedIndex());
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    acceptSelected();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.setVisible(false);
                    e.consume();
                }
            }
        });

        // --- Click on a suggestion to accept it ---
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                acceptSelected();
            }
        });

        // --- Close popup when focus leaves the field ---
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Small delay so a list click can register before the popup disappears
                SwingUtilities.invokeLater(() -> popup.setVisible(false));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Replaces the full suggestion list.
     * Typically called by {@code MainFrame} right after a map file is opened.
     */
    public void setSuggestions(List<UnitEntry> entries) {
        allEntries = (entries != null) ? entries : Collections.emptyList();
        popup.setVisible(false);
    }

    /**
     * Returns the current text in the field — normally a 4-character unit ID
     * (either typed manually or chosen from the suggestion popup).
     */
    public String getValue() {
        return field.getText().trim();
    }

    /**
     * Sets the text in the field programmatically without triggering the suggestion filter.
     */
    public void setValue(String value) {
        suppressFilter = true;
        try {
            field.setText(value != null ? value : "");
        } finally {
            suppressFilter = false;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void onTextChanged() {
        if (suppressFilter) return;

        String query = field.getText();
        List<UnitEntry> filtered = allEntries.stream()
                .filter(e -> e.matches(query))
                .limit(20)
                .collect(Collectors.toList());

        listModel.clear();
        for (UnitEntry e : filtered) {
            listModel.addElement(e);
        }

        if (filtered.isEmpty()) {
            popup.setVisible(false);
            return;
        }

        // Size the popup to fit the entries (capped at MAX_VISIBLE_ROWS)
        int rows = Math.min(filtered.size(), MAX_VISIBLE_ROWS);
        int width = Math.max(field.getWidth(), MIN_POPUP_WIDTH);
        scrollPane.setPreferredSize(new Dimension(width, rows * ROW_HEIGHT));
        popup.pack();

        if (!popup.isVisible()) {
            popup.show(field, 0, field.getHeight());
        }
    }

    private void acceptSelected() {
        UnitEntry entry = suggestionList.getSelectedValue();
        if (entry != null) {
            suppressFilter = true;
            field.setText(entry.id());
            suppressFilter = false;
            popup.setVisible(false);
        }
    }
}
