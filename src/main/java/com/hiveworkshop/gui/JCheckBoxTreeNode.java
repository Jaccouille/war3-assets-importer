package org.example.gui;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Tree node with tri-state checkbox support (checked / unchecked / partial).
 * Used by {@link JCheckBoxTree}.
 */
public class JCheckBoxTreeNode extends DefaultMutableTreeNode {

    private boolean checked;
    private boolean allChildrenSelected;
    private boolean hasPersonalState; // NYI, do not use

    public JCheckBoxTreeNode() { super(); }

    public JCheckBoxTreeNode(Object userObject, boolean checked) {
        super(userObject);
        this.checked = checked;
    }

    public JCheckBoxTreeNode(Object userObject) { super(userObject); }

    public JCheckBoxTreeNode(boolean checked) {
        super();
        this.checked = checked;
    }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }

    public boolean isAllChildrenSelected() { return allChildrenSelected; }
    public void setAllChildrenSelected(boolean v) { this.allChildrenSelected = v; }

    // NYI, buggy — do not use
    public boolean isHasPersonalState() { return hasPersonalState; }
    public void setHasPersonalState(boolean v) { this.hasPersonalState = v; }
}
