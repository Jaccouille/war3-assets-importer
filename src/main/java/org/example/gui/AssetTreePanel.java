package org.example.gui;

import org.example.gui.i18n.Messages;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Panel containing the hierarchical asset selection tree.
 *
 * <p>Displays MDX and BLP files organised by folder, with tri-state checkboxes.
 * Holding Ctrl while expanding/collapsing applies the action recursively.
 */
public class AssetTreePanel extends JPanel {

    /** Callback fired when the user focuses a leaf node (file) in the tree. */
    public interface AssetSelectionListener {
        void onAssetSelected(String relativePath);
    }

    /** Callback fired when the user focuses a folder node in the tree. */
    public interface FolderSelectionListener {
        /** @param blpRelativePaths relative paths of BLP files under the folder (stripped of category prefix). */
        void onFolderSelected(List<String> blpRelativePaths);
    }

    private final JCheckBoxTree assetTree;
    private final DefaultTreeModel treeModel;
    private boolean controlDown = false;
    private boolean isTreeUpdating = false;
    private File modelsFolder;
    private AssetSelectionListener selectionListener;
    private FolderSelectionListener folderListener;

    public AssetTreePanel() {
        setLayout(new java.awt.BorderLayout());

        TreeNodeData rootData = new TreeNodeData(Messages.get("tree.root"), false, "", 0, 0);
        JCheckBoxTreeNode root = new JCheckBoxTreeNode(rootData, true);
        treeModel = new DefaultTreeModel(root);
        assetTree = new JCheckBoxTree(treeModel);

        JScrollPane treeScrollPane = new JScrollPane(assetTree);
        treeScrollPane.setPreferredSize(new java.awt.Dimension(200, 400));
        add(treeScrollPane, java.awt.BorderLayout.CENTER);

        setupExpandCollapseBehavior();
        assetTree.addTreeSelectionListener(e -> onTreeSelect());
        // Text-click (no toggle) → still update the preview panel
        assetTree.setRowFocusCallback(this::notifyFocusedRowChange);

        assetTree.addCheckChangeEventListener(evt -> {
            Object nodeObj = evt.getSource();
            if (!(nodeObj instanceof JCheckBoxTreeNode node)) return;
            if (node.isLeaf()) {
                TreeNodeData data = (TreeNodeData) node.getUserObject();
                // Strip the category prefix (e.g. "BLP Files/" or "MDX Files/")
                String rel = data.relativePath();
                int slash = rel.indexOf('/');
                if (slash >= 0) rel = rel.substring(slash + 1);
                if (selectionListener != null) selectionListener.onAssetSelected(rel);
            } else {
                if (folderListener != null) folderListener.onFolderSelected(collectBlpPathsUnder(node));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setAssetSelectionListener(AssetSelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setFolderSelectionListener(FolderSelectionListener listener) {
        this.folderListener = listener;
    }

    public void setModelsFolder(File folder) {
        this.modelsFolder = folder;
    }

    public JTree getTree() {
        return assetTree;
    }

    /**
     * Rebuilds the tree from the given file lists.
     * Called after the user picks a models folder.
     */
    public void updateTree(List<String> mdxFiles, List<String> blpFiles) {
        JCheckBoxTreeNode mdxNode = buildFolderTree(Messages.get("tree.mdx"), mdxFiles);
        JCheckBoxTreeNode blpNode = buildFolderTree(Messages.get("tree.blp"), blpFiles);

        TreeNodeData mdxData = (TreeNodeData) mdxNode.getUserObject();
        TreeNodeData blpData = (TreeNodeData) blpNode.getUserObject();
        int totalFiles = mdxData.fileCount() + blpData.fileCount();
        long totalSize = mdxData.sizeInBytes() + blpData.sizeInBytes();

        TreeNodeData rootData = new TreeNodeData(
                Messages.get("tree.root"), false, "", totalSize, totalFiles);
        JCheckBoxTreeNode root = new JCheckBoxTreeNode(rootData, false);
        root.add(mdxNode);
        root.add(blpNode);

        treeModel.setRoot(root);
        treeModel.reload();
        assetTree.resetCheckingState();
    }

    /**
     * Returns absolute Paths for all checked leaf nodes (files) in the tree.
     */
    public Set<Path> getCheckedFiles() {
        Set<Path> result = new LinkedHashSet<>();
        if (modelsFolder == null) return result;

        TreeNode root = (TreeNode) treeModel.getRoot();
        collectChecked(root, result);
        return result;
    }

    /** Refreshes i18n labels. Called when the locale changes. */
    public void applyI18n() {
        // Rebuild the root label — a full tree rebuild with preserved checks is complex;
        // since the folder hasn't changed, a simple updateTree re-read suffices if
        // the folder is known. For the root-only label, update the root node directly.
        TreeNode root = (TreeNode) treeModel.getRoot();
        if (root instanceof JCheckBoxTreeNode cbRoot) {
            Object data = cbRoot.getUserObject();
            if (data instanceof TreeNodeData td) {
                cbRoot.setUserObject(new TreeNodeData(
                        Messages.get("tree.root"), td.isFile(), td.relativePath(),
                        td.sizeInBytes(), td.fileCount()));
            }
        }
        treeModel.reload();
    }

    // -------------------------------------------------------------------------
    // Tree selection
    // -------------------------------------------------------------------------

    private void onTreeSelect() {
        TreePath path = assetTree.getSelectionPath();
        if (path == null) return;
        Object last = path.getLastPathComponent();
        if (!(last instanceof JCheckBoxTreeNode node) || !node.isLeaf()) return;

        StringBuilder sb = new StringBuilder();
        Object[] components = path.getPath();
        // Skip root ("Model Assets") and category ("MDX Files (…)")
        for (int i = 2; i < components.length; i++) {
            String part = components[i].toString().replaceAll("\\s*\\(.*?\\)$", "").trim();
            sb.append(part);
            if (i < components.length - 1) sb.append("/");
        }
        if (selectionListener != null) selectionListener.onAssetSelected(sb.toString());
    }

    /**
     * Fires the appropriate selection callback for the node at the given tree row,
     * used when keyboard focus moves via UP/DOWN arrow keys.
     * <ul>
     *   <li>Leaf nodes → {@link AssetSelectionListener#onAssetSelected}</li>
     *   <li>Folder nodes → {@link FolderSelectionListener#onFolderSelected} with all BLP files under it</li>
     * </ul>
     */
    private void notifyFocusedRowChange(int row) {
        TreePath tp = assetTree.getPathForRow(row);
        if (tp == null) return;
        Object last = tp.getLastPathComponent();
        if (!(last instanceof JCheckBoxTreeNode node)) return;
        if (node.isLeaf()) {
            if (selectionListener == null) return;
            TreeNodeData data = (TreeNodeData) node.getUserObject();
            String rel = data.relativePath();
            int slash = rel.indexOf('/');
            if (slash >= 0) rel = rel.substring(slash + 1);
            selectionListener.onAssetSelected(rel);
        } else {
            if (folderListener != null) folderListener.onFolderSelected(collectBlpPathsUnder(node));
        }
    }

    // -------------------------------------------------------------------------
    // Tree building
    // -------------------------------------------------------------------------

    private JCheckBoxTreeNode buildFolderTree(String label, List<String> filePaths) {
        FolderNode folderRoot = new FolderNode(label);
        for (String rel : filePaths) {
            String[] parts = rel.split("/");
            FolderNode cur = folderRoot;
            for (int i = 0; i < parts.length; i++) {
                boolean leaf = i == parts.length - 1;
                cur = cur.getChildOrCreate(parts[i], leaf);
                if (leaf) {
                    cur.incrementFileCount();
                    try {
                        long size = Files.size(Paths.get(modelsFolder.getAbsolutePath(), rel));
                        cur.addSize(size);
                    } catch (IOException ignored) {}
                }
            }
        }
        folderRoot.recalculateCountsAndSizes();
        return toCheckBoxNode(folderRoot, "");
    }

    private JCheckBoxTreeNode toCheckBoxNode(FolderNode fn, String parentPath) {
        String fullPath = parentPath.isEmpty() ? fn.getName() : parentPath + "/" + fn.getName();
        TreeNodeData data = new TreeNodeData(
                fn.getName(), fn.isFile(), fullPath, fn.getTotalSize(), fn.getFileCount());
        JCheckBoxTreeNode node = new JCheckBoxTreeNode(data, false);
        for (FolderNode child : fn.getChildren().values()) {
            node.add(toCheckBoxNode(child, fullPath));
        }
        return node;
    }

    private void collectChecked(TreeNode treeNode, Set<Path> result) {
        if (!(treeNode instanceof JCheckBoxTreeNode cbNode)) return;
        if (cbNode.isLeaf() && cbNode.isChecked()) {
            TreeNodeData data = (TreeNodeData) cbNode.getUserObject();
            if (data.isFile() && modelsFolder != null) {
                // Strip the leading category prefix (e.g. "MDX Files/" or "BLP Files/")
                String rel = data.relativePath();
                int slash = rel.indexOf('/');
                if (slash >= 0) rel = rel.substring(slash + 1);
                result.add(modelsFolder.toPath().resolve(rel).normalize());
            }
        }
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            collectChecked(treeNode.getChildAt(i), result);
        }
    }

    // -------------------------------------------------------------------------
    // Ctrl+click expand/collapse all
    // -------------------------------------------------------------------------

    private void setupExpandCollapseBehavior() {
        assetTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                controlDown = e.isControlDown();
                int current = assetTree.getFocusedRow();
                int rowCount = assetTree.getRowCount();
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (rowCount > 0) {
                        int next = (current <= 0) ? 0 : current - 1;
                        assetTree.setFocusedRow(next);
                        notifyFocusedRowChange(next);
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (rowCount > 0) {
                        int next = (current < 0) ? 0 : Math.min(current + 1, rowCount - 1);
                        assetTree.setFocusedRow(next);
                        notifyFocusedRowChange(next);
                    }
                    e.consume();
                }
            }
            @Override public void keyReleased(KeyEvent e) { controlDown = e.isControlDown(); }
        });

        assetTree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                if (controlDown && !isTreeUpdating) {
                    isTreeUpdating = true;
                    SwingUtilities.invokeLater(() -> {
                        expandAllChildren(event.getPath(), true);
                        isTreeUpdating = false;
                    });
                }
            }
            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                if (controlDown && !isTreeUpdating) {
                    isTreeUpdating = true;
                    SwingUtilities.invokeLater(() -> {
                        expandAllChildren(event.getPath(), false);
                        isTreeUpdating = false;
                    });
                }
            }
        });
    }

    private void expandAllChildren(TreePath path, boolean expand) {
        TreeNode node = (TreeNode) path.getLastPathComponent();
        for (int i = 0; i < node.getChildCount(); i++) {
            expandAllChildren(path.pathByAddingChild(node.getChildAt(i)), expand);
        }
        if (expand) assetTree.expandPath(path);
        else assetTree.collapsePath(path);
    }

    /**
     * Collects the relative paths (category prefix already stripped) of all
     * BLP leaf files that are descendants of {@code node}.
     */
    private List<String> collectBlpPathsUnder(JCheckBoxTreeNode node) {
        List<String> result = new ArrayList<>();
        collectBlpPathsRecursive(node, result);
        return result;
    }

    private void collectBlpPathsRecursive(JCheckBoxTreeNode node, List<String> result) {
        if (node.isLeaf()) {
            TreeNodeData data = (TreeNodeData) node.getUserObject();
            if (data.isFile()) {
                String rel = data.relativePath();
                int slash = rel.indexOf('/');
                if (slash >= 0) rel = rel.substring(slash + 1);
                if (rel.toLowerCase().endsWith(".blp")) {
                    result.add(rel);
                }
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof JCheckBoxTreeNode cb) {
                collectBlpPathsRecursive(cb, result);
            }
        }
    }

    // -------------------------------------------------------------------------
    // FolderNode — internal tree building helper
    // -------------------------------------------------------------------------

    private static class FolderNode {
        private final String name;
        private final boolean isFile;
        private final Map<String, FolderNode> children = new LinkedHashMap<>();
        private int fileCount = 0;
        private long totalSize = 0;

        FolderNode(String name, boolean isFile) { this.name = name; this.isFile = isFile; }
        FolderNode(String name) { this(name, false); }

        FolderNode getChildOrCreate(String name, boolean leaf) {
            return children.computeIfAbsent(name, n -> new FolderNode(n, leaf));
        }

        String getName() { return name; }
        boolean isFile() { return isFile; }
        int getFileCount() { return fileCount; }
        long getTotalSize() { return totalSize; }
        Map<String, FolderNode> getChildren() { return children; }
        void incrementFileCount() { fileCount++; }
        void addSize(long size) { totalSize += size; }

        int recalculateCountsAndSizes() {
            if (isFile) return fileCount;
            fileCount = 0; totalSize = 0;
            for (FolderNode child : children.values()) {
                fileCount += child.recalculateCountsAndSizes();
                totalSize += child.totalSize;
            }
            return fileCount;
        }
    }
}
