package org.example.gui.settings;

import org.example.gui.i18n.Messages;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Settings panel for customising keyboard shortcuts.
 *
 * <p>Displays a two-column table of action name → current shortcut.
 * Clicking a row puts it into "capture" mode: the next key combination
 * typed by the user is recorded and stored in the config.
 */
public class KeybindingPanel extends JPanel {

    /** Maps action id → i18n display name. */
    private static final Map<String, String> ACTION_KEYS = Map.of(
            KeybindingsConfig.ACTION_OPEN_MAP, "action.openMap",
            KeybindingsConfig.ACTION_IMPORT_MODELS, "action.importModels",
            KeybindingsConfig.ACTION_PROCESS, "action.process",
            KeybindingsConfig.ACTION_SETTINGS, "action.settings"
    );

    private final KeybindingsConfig config;
    private final BindingTableModel tableModel;
    private final JTable table;
    private int capturingRow = -1;  // index of the row currently in capture mode

    public KeybindingPanel(KeybindingsConfig config) {
        this.config = config;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tableModel = new BindingTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFocusable(true);
        table.getTableHeader().setReorderingAllowed(false);

        // Highlight the "capturing" row in a muted orange
        table.setDefaultRenderer(Object.class, new CapturingCellRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Reset-to-defaults button
        JButton resetBtn = new JButton(Messages.get("settings.keybindings.resetDefaults"));
        resetBtn.addActionListener(e -> {
            config.resetToDefaults();
            tableModel.reload();
            capturingRow = -1;
            table.repaint();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(resetBtn);
        add(south, BorderLayout.SOUTH);

        JLabel hint = new JLabel(Messages.get("settings.keybindings.clickToRebind"));
        hint.setForeground(Color.GRAY);
        add(hint, BorderLayout.NORTH);

        // Click → enter capture mode
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    enterCaptureMode(row);
                }
            }
        });

        // Key capture
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (capturingRow < 0) return;

                // Ignore modifier-only key presses
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL
                        || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META) {
                    return;
                }
                // Escape cancels capture mode
                if (code == KeyEvent.VK_ESCAPE) {
                    exitCaptureMode();
                    return;
                }

                String ksText = buildKeyStrokeText(e);
                String action = tableModel.getActionId(capturingRow);
                config.setBinding(action, ksText);
                tableModel.reload();
                exitCaptureMode();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Capture helpers
    // -------------------------------------------------------------------------

    private void enterCaptureMode(int row) {
        capturingRow = row;
        tableModel.setCapturingRow(row);
        table.repaint();
        table.requestFocusInWindow();
    }

    private void exitCaptureMode() {
        capturingRow = -1;
        tableModel.setCapturingRow(-1);
        table.repaint();
    }

    /** Rebuilds a keystroke string from a KeyEvent (e.g. "ctrl shift O"). */
    private static String buildKeyStrokeText(KeyEvent e) {
        StringBuilder sb = new StringBuilder();
        if (e.isControlDown()) sb.append("ctrl ");
        if (e.isAltDown()) sb.append("alt ");
        if (e.isShiftDown()) sb.append("shift ");
        if (e.isMetaDown()) sb.append("meta ");
        sb.append(KeyEvent.getKeyText(e.getKeyCode()).toUpperCase());
        return sb.toString().trim();
    }

    /** Refreshes labels after a locale change. */
    public void applyI18n() {
        tableModel.fireTableStructureChanged();
    }

    // -------------------------------------------------------------------------
    // Table model
    // -------------------------------------------------------------------------

    private class BindingTableModel extends AbstractTableModel {

        private final List<String> actionIds = new ArrayList<>(ACTION_KEYS.keySet());
        private int capturingRow = -1;

        public void setCapturingRow(int row) { this.capturingRow = row; }

        public String getActionId(int row) { return actionIds.get(row); }

        @Override public int getRowCount() { return actionIds.size(); }
        @Override public int getColumnCount() { return 2; }

        @Override
        public String getColumnName(int col) {
            return col == 0
                    ? Messages.get("settings.keybindings.column.action")
                    : Messages.get("settings.keybindings.column.shortcut");
        }

        @Override
        public Object getValueAt(int row, int col) {
            String actionId = actionIds.get(row);
            if (col == 0) return Messages.get(ACTION_KEYS.get(actionId));
            if (row == capturingRow) return Messages.get("settings.keybindings.pressKey");
            String binding = config.getBinding(actionId);
            return binding != null ? binding : "";
        }

        public void reload() { fireTableDataChanged(); }
    }

    // -------------------------------------------------------------------------
    // Cell renderer
    // -------------------------------------------------------------------------

    private class CapturingCellRenderer extends DefaultTableCellRenderer {
        private static final Color CAPTURE_BG = new Color(255, 220, 150);

        @Override
        public Component getTableCellRendererComponent(
                JTable tbl, Object value, boolean selected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(tbl, value, selected, hasFocus, row, col);
            if (row == capturingRow) {
                setBackground(CAPTURE_BG);
                setFont(getFont().deriveFont(Font.ITALIC));
            } else {
                setBackground(selected ? tbl.getSelectionBackground() : tbl.getBackground());
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            return this;
        }
    }
}
