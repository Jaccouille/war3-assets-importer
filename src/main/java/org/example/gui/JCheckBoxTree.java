package org.example.gui;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.Serial;
import java.util.EventListener;
import java.util.EventObject;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * JTree with tri-state checkbox cell rendering.
 * Based on a Stack Overflow implementation.
 *
 * @see <a href="https://stackoverflow.com/questions/21847411/java-swing-need-a-good-quality-developed-jtree-with-checkboxes">SO source</a>
 */
public class JCheckBoxTree extends JTree {

    @Serial
    private static final long serialVersionUID = -4194122328392241790L;

    protected EventListenerList listenerList = new EventListenerList();
    HashSet<TreeNode> checkedPaths = new HashSet<>();
    private int focusedRow = -1;

    /** Optional callback fired when a row is focused via a text-click (no toggle). */
    private Consumer<Integer> rowFocusCallback;

    public JCheckBoxTree(final TreeModel treeModel) {
        super(treeModel);
        this.setToggleClickCount(0);
        this.setCellRenderer(new CheckBoxCellRenderer());

        // Disable standard selection — checkboxes are the only selection mechanism
        final DefaultTreeSelectionModel dtsm = new DefaultTreeSelectionModel() {
            @Serial private static final long serialVersionUID = -8190634240451667286L;
            @Override public void setSelectionPath(TreePath path) {}
            @Override public void addSelectionPath(TreePath path) {}
            @Override public void removeSelectionPath(TreePath path) {}
            @Override public void setSelectionPaths(TreePath[] pPaths) {}
        };

        this.addMouseListener(new MouseListener() {
            @Override public void mouseClicked(MouseEvent e) {}
            @Override public void mouseEntered(MouseEvent e) {}
            @Override public void mouseExited(MouseEvent e) {}
            @Override public void mousePressed(MouseEvent e) {}

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) return;
                final TreePath tp = JCheckBoxTree.this.getPathForLocation(e.getX(), e.getY());
                if (tp == null) return;
                final int clickedRow = JCheckBoxTree.this.getRowForPath(tp);
                if (clickedRow >= 0) focusedRow = clickedRow;

                // Only toggle the check-state when the click lands on the checkbox
                // icon itself.  Clicks on the text label update focus/preview only.
                if (!isClickOnCheckbox(e, tp, clickedRow)) {
                    JCheckBoxTree.this.repaint();
                    if (rowFocusCallback != null) rowFocusCallback.accept(clickedRow);
                    return;
                }

                final boolean checkMode = !((JCheckBoxTreeNode) tp.getLastPathComponent()).isChecked();
                checkSubTree(tp, checkMode);
                updatePredecessorsWithCheckMode(tp, checkMode);
                fireCheckChangeEvent(new CheckChangeEvent(tp.getLastPathComponent(), tp));
                JCheckBoxTree.this.repaint();
            }
        });
        this.setSelectionModel(dtsm);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void addCheckChangeEventListener(CheckChangeEventListener listener) {
        listenerList.add(CheckChangeEventListener.class, listener);
    }

    public void removeCheckChangeEventListener(CheckChangeEventListener listener) {
        listenerList.remove(CheckChangeEventListener.class, listener);
    }

    void fireCheckChangeEvent(CheckChangeEvent evt) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] == CheckChangeEventListener.class) {
                ((CheckChangeEventListener) listeners[i + 1]).checkStateChanged(evt);
            }
        }
    }

    @Override
    public void setModel(TreeModel newModel) {
        super.setModel(newModel);
        resetCheckingState();
    }

    public TreeNode[] getCheckedPaths() {
        return checkedPaths.toArray(new TreeNode[0]);
    }

    public boolean isSelectedPartially(JCheckBoxTreeNode node) {
        return node.isChecked() && (node.getChildCount() > 0) && !node.isAllChildrenSelected();
    }

    public boolean isSelected(JCheckBoxTreeNode node) {
        return node.isChecked();
    }

    public void resetCheckingState() {
        checkedPaths = new HashSet<>();
        JCheckBoxTreeNode node = (JCheckBoxTreeNode) getModel().getRoot();
        if (node == null) return;
        checkAllCheckedRecursively(node);
    }

    public int getFocusedRow() { return focusedRow; }

    public void setFocusedRow(int row) {
        focusedRow = row;
        if (row >= 0) scrollRowToVisible(row);
        repaint();
    }

    /**
     * Registers a callback that is invoked when the user clicks on the <em>text</em>
     * portion of a node (as opposed to the checkbox icon).  This allows the host panel
     * to update a preview without toggling the check-state.
     *
     * @param callback receives the clicked row index, or {@code null} to unregister
     */
    public void setRowFocusCallback(Consumer<Integer> callback) {
        this.rowFocusCallback = callback;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the mouse event coordinates fall within the checkbox
     * icon area of the cell renderer — i.e. the user clicked the checkbox itself,
     * not the text label beside it.
     *
     * <p>Strategy: render the cell, lay it out, then measure the checkbox icon
     * width using {@link UIManager} (or a sensible fallback) and the panel insets.
     */
    private boolean isClickOnCheckbox(MouseEvent e, TreePath tp, int row) {
        Rectangle cellBounds = getPathBounds(tp);
        if (cellBounds == null) return true;            // can't measure — allow toggle

        TreeCellRenderer tcr = getCellRenderer();
        if (!(tcr instanceof CheckBoxCellRenderer cbr)) return true;

        // Ask the renderer to prepare itself for this cell so it has correct insets
        cbr.getTreeCellRendererComponent(
                JCheckBoxTree.this,
                tp.getLastPathComponent(),
                false,
                isExpanded(tp),
                ((JCheckBoxTreeNode) tp.getLastPathComponent()).isLeaf(),
                row,
                false);
        cbr.setSize(cellBounds.width, cellBounds.height);
        cbr.doLayout();

        // The JCheckBox fills the whole panel (BorderLayout.CENTER); its icon sits
        // at the left edge plus the panel's left inset.
        Icon icon = UIManager.getIcon("CheckBox.icon");
        int iconWidth = (icon != null) ? icon.getIconWidth() : 16;
        Insets panelInsets = cbr.getInsets();   // typically (0,0,0,0) for our renderer
        Insets cbInsets   = cbr.checkBox.getInsets();

        // Right edge of the icon in component-space → translate to screen x
        int iconRight = cellBounds.x + panelInsets.left + cbInsets.left + iconWidth + 2;

        return e.getX() <= iconRight;
    }

    private boolean checkAllCheckedRecursively(JCheckBoxTreeNode node) {
        final TreeNode[] path = node.getPath();
        final TreePath tp = new TreePath(path);
        boolean allChildrenChecked = true;
        boolean anyChildChecked = false;

        for (int i = 0; i < node.getChildCount(); i++) {
            final TreeNode childAt = node.getChildAt(i);
            if (!checkAllCheckedRecursively(
                    (JCheckBoxTreeNode) tp.pathByAddingChild(childAt).getLastPathComponent())) {
                allChildrenChecked = false;
            }
            if (((JCheckBoxTreeNode) childAt).isChecked()) anyChildChecked = true;
        }

        if ((node.getChildCount() > 0) && anyChildChecked && !node.isHasPersonalState()) {
            node.setChecked(true);
        }
        if (!node.isHasPersonalState()) {
            node.setAllChildrenSelected(allChildrenChecked);
        }
        return allChildrenChecked && node.isChecked();
    }

    protected void updatePredecessorsWithCheckMode(TreePath tp, boolean check) {
        TreePath parentPath = tp.getParentPath();
        if (parentPath == null) return;

        JCheckBoxTreeNode parentChecked = (JCheckBoxTreeNode) parentPath.getLastPathComponent();
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
        parentChecked.setAllChildrenSelected(true);
        parentChecked.setChecked(false);

        for (int i = 0; i < parentNode.getChildCount(); i++) {
            TreePath childPath = parentPath.pathByAddingChild(parentNode.getChildAt(i));
            JCheckBoxTreeNode childChecked = (JCheckBoxTreeNode) childPath.getLastPathComponent();
            if (!childChecked.isAllChildrenSelected()) parentChecked.setAllChildrenSelected(false);
            if (childChecked.isChecked() && !parentChecked.isHasPersonalState()) parentChecked.setChecked(true);
        }

        if (parentChecked.isChecked()) {
            checkedPaths.add((TreeNode) parentPath.getLastPathComponent());
        } else {
            checkedPaths.remove(parentPath.getLastPathComponent());
        }
        updatePredecessorsWithCheckMode(parentPath, check);
    }

    protected void checkSubTree(TreePath tp, boolean check) {
        JCheckBoxTreeNode cn = (JCheckBoxTreeNode) tp.getLastPathComponent();
        cn.setChecked(check);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
        if (!cn.isHasPersonalState()) {
            for (int i = 0; i < node.getChildCount(); i++) {
                checkSubTree(tp.pathByAddingChild(node.getChildAt(i)), check);
            }
            cn.setAllChildrenSelected(check);
        }
        if (check) checkedPaths.add((TreeNode) tp.getLastPathComponent());
        else checkedPaths.remove(tp.getLastPathComponent());
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    public interface CheckChangeEventListener extends EventListener {
        void checkStateChanged(CheckChangeEvent event);
    }

    public class CheckChangeEvent extends EventObject {
        private static final long serialVersionUID = -8100230309044193368L;
        private final TreePath treePath;

        public CheckChangeEvent(Object source, TreePath path) {
            super(source);
            this.treePath = path;
        }

        public TreePath getTreePath() { return treePath; }
    }

    private class CheckBoxCellRenderer extends JPanel implements TreeCellRenderer {
        private static final long serialVersionUID = -7341833835878991719L;
        JCheckBox checkBox;

        public CheckBoxCellRenderer() {
            setLayout(new BorderLayout());
            checkBox = new JCheckBox();
            add(checkBox, BorderLayout.CENTER);
            setOpaque(false);
        }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            JCheckBoxTreeNode node = (JCheckBoxTreeNode) value;
            checkBox.setSelected(node.isChecked());
            checkBox.setText(node.getUserObject().toString());

            boolean isKeyFocused = row == JCheckBoxTree.this.focusedRow;
            boolean isPartial = node.isChecked() && node.getChildCount() > 0 && !node.isAllChildrenSelected();

            if (isKeyFocused) {
                setOpaque(true);
                setBackground(UIManager.getColor("Tree.selectionBackground"));
                checkBox.setForeground(UIManager.getColor("Tree.selectionForeground"));
                checkBox.setOpaque(false);
            } else {
                setOpaque(false);
                checkBox.setForeground(UIManager.getColor("Tree.textForeground"));
                checkBox.setOpaque(isPartial);
            }
            return this;
        }
    }
}
