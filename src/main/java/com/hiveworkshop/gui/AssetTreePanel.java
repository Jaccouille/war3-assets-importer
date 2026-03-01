package org.example.gui;

import org.example.gui.i18n.Messages;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Panel containing the hierarchical asset selection tree.
 *
 * <p>Offers two views toggled by radio buttons:
 * <ul>
 *   <li><b>Category View</b> — MDX and texture files grouped into category nodes
 *       (the original behaviour).</li>
 *   <li><b>Folder View</b> — the raw filesystem tree rooted at the models folder,
 *       with lazy expansion and identical tri-state checkbox behaviour.</li>
 * </ul>
 *
 * <p>{@link #getCheckedFiles()} always returns checked files from whichever view is active.
 */
public class AssetTreePanel extends JPanel {

    /** Callback fired when the user focuses a single file node. */
    public interface AssetSelectionListener {
        void onAssetSelected(File file);
    }

    /** Callback fired when the user focuses a folder node. */
    public interface FolderSelectionListener {
        void onFolderSelected(List<File> textureFiles);
    }

    /** Marker stored as the user-object of a not-yet-expanded sentinel child node. */
    private static final String SENTINEL = "__LAZY__";

    // ---- Category (asset) tree ----
    private final JCheckBoxTree assetTree;
    private final DefaultTreeModel treeModel;
    private final java.util.IdentityHashMap<JCheckBoxTreeNode, FolderNode> pendingNodes
            = new java.util.IdentityHashMap<>();

    // ---- Filesystem (folder) tree ----
    private final JCheckBoxTree folderTree;
    private final DefaultTreeModel folderTreeModel;
    private final java.util.IdentityHashMap<JCheckBoxTreeNode, File> pendingFolderDirs
            = new java.util.IdentityHashMap<>();

    // ---- Shared UI ----
    private final JPanel cardPanel;
    private final JRadioButton categoryBtn;
    private final JRadioButton folderBtn;
    private boolean isFolderTreeActive = false;

    // ---- Shared state ----
    private boolean controlDown   = false;
    private boolean isTreeUpdating = false;
    private File modelsFolder;
    private AssetSelectionListener selectionListener;
    private FolderSelectionListener folderListener;
    private ImportConfigPanel importConfigPanel;

    public AssetTreePanel() {
        setLayout(new java.awt.BorderLayout());

        // ---- Category tree ----
        TreeNodeData rootData = new TreeNodeData(Messages.get("tree.root"), false, "", 0, 0);
        JCheckBoxTreeNode root = new JCheckBoxTreeNode(rootData, true);
        treeModel = new DefaultTreeModel(root);
        assetTree = new JCheckBoxTree(treeModel);

        // ---- Folder tree ----
        folderTreeModel = new DefaultTreeModel(
                new JCheckBoxTreeNode(Messages.get("tree.noFolderLoaded"), false));
        folderTree = new JCheckBoxTree(folderTreeModel);

        // ---- Toggle panel ----
        categoryBtn = new JRadioButton(Messages.get("tree.categoryView"), true);
        folderBtn   = new JRadioButton(Messages.get("tree.folderView"));
        ButtonGroup viewGroup = new ButtonGroup();
        viewGroup.add(categoryBtn);
        viewGroup.add(folderBtn);
        JPanel togglePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        togglePanel.add(categoryBtn);
        togglePanel.add(folderBtn);
        add(togglePanel, java.awt.BorderLayout.NORTH);

        // ---- Card panel ----
        JScrollPane assetScrollPane = new JScrollPane(assetTree);
        assetScrollPane.setPreferredSize(new java.awt.Dimension(200, 400));
        JScrollPane folderScrollPane = new JScrollPane(folderTree);
        folderScrollPane.setPreferredSize(new java.awt.Dimension(200, 400));
        cardPanel = new JPanel(new java.awt.CardLayout());
        cardPanel.add(assetScrollPane, "category");
        cardPanel.add(folderScrollPane, "folder");
        add(cardPanel, java.awt.BorderLayout.CENTER);

        categoryBtn.addActionListener(e -> switchView(false));
        folderBtn.addActionListener(e -> switchView(true));

        // ---- Wire category tree ----
        setupExpandCollapseBehavior();
        setupLazyExpansion();
        assetTree.addTreeSelectionListener(e -> onTreeSelect());
        assetTree.setRowFocusCallback(this::notifyFocusedRowChange);
        assetTree.addCheckChangeEventListener(evt -> {
            Object nodeObj = evt.getSource();
            if (!(nodeObj instanceof JCheckBoxTreeNode node)) return;
            if (node.isLeaf()) {
                if (selectionListener != null && modelsFolder != null) {
                    TreeNodeData data = (TreeNodeData) node.getUserObject();
                    selectionListener.onAssetSelected(
                            new File(modelsFolder, stripCategoryPrefix(data.relativePath())));
                }
            } else {
                if (folderListener != null)
                    folderListener.onFolderSelected(collectTextureFilesUnder(node));
            }
            updateImportConfigPanel();
        });

        // ---- Wire folder tree ----
        setupFolderTreeLazyExpansion();
        folderTree.addTreeSelectionListener(e -> onFolderTreeSelect());
        folderTree.setRowFocusCallback(this::notifyFolderTreeFocusedRowChange);
        folderTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int current  = folderTree.getFocusedRow();
                int rowCount = folderTree.getRowCount();
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (rowCount > 0) {
                        int next = current <= 0 ? 0 : current - 1;
                        folderTree.setFocusedRow(next);
                        notifyFolderTreeFocusedRowChange(next);
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (rowCount > 0) {
                        int next = current < 0 ? 0 : Math.min(current + 1, rowCount - 1);
                        folderTree.setFocusedRow(next);
                        notifyFolderTreeFocusedRowChange(next);
                    }
                    e.consume();
                }
            }
            @Override public void keyReleased(KeyEvent e) {}
        });
        folderTree.addCheckChangeEventListener(evt -> {
            Object nodeObj = evt.getSource();
            if (!(nodeObj instanceof JCheckBoxTreeNode node)) return;
            if (!(node.getUserObject() instanceof FileNodeData fnd)) return;
            File file = fnd.file();
            if (file.isFile()) {
                if (selectionListener != null) selectionListener.onAssetSelected(file);
            } else {
                if (folderListener != null)
                    folderListener.onFolderSelected(collectTextureFilesFromFolderTreeNode(node));
            }
            updateImportConfigPanel();
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

    public void setImportConfigPanel(ImportConfigPanel panel) {
        this.importConfigPanel = panel;
    }

    public void setModelsFolder(File folder) {
        this.modelsFolder = folder;
    }

    public JTree getTree() { return assetTree; }

    /**
     * Rebuilds both trees from the discovered file lists.
     * Called after the user picks a models folder.
     */
    public void updateTree(List<String> mdxFiles, List<String> textureFiles,
                           Map<String, Long> fileSizes) {
        // ---- Category tree ----
        pendingNodes.clear();
        JCheckBoxTreeNode mdxNode = buildCategoryTree(Messages.get("tree.mdx"), mdxFiles, fileSizes);
        JCheckBoxTreeNode blpNode = buildCategoryTree(Messages.get("tree.textures"), textureFiles, fileSizes);

        TreeNodeData mdxData = (TreeNodeData) mdxNode.getUserObject();
        TreeNodeData blpData = (TreeNodeData) blpNode.getUserObject();
        TreeNodeData rootData = new TreeNodeData(
                Messages.get("tree.root"), false, "",
                mdxData.sizeInBytes() + blpData.sizeInBytes(),
                mdxData.fileCount() + blpData.fileCount());
        JCheckBoxTreeNode newRoot = new JCheckBoxTreeNode(rootData, false);
        newRoot.add(mdxNode);
        newRoot.add(blpNode);
        treeModel.setRoot(newRoot);
        treeModel.reload();
        assetTree.resetCheckingState();

        // ---- Folder tree ----
        if (modelsFolder != null) rebuildFolderTree();
    }

    /**
     * Returns absolute Paths for all checked files in the currently active tree view.
     */
    public Set<Path> getCheckedFiles() {
        if (isFolderTreeActive) return getCheckedFilesFromFolderTree();
        Set<Path> result = new LinkedHashSet<>();
        if (modelsFolder == null) return result;
        collectChecked((TreeNode) treeModel.getRoot(), result);
        return result;
    }

    /** Refreshes i18n labels after a locale change. */
    public void applyI18n() {
        categoryBtn.setText(Messages.get("tree.categoryView"));
        folderBtn.setText(Messages.get("tree.folderView"));
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
    // View toggle
    // -------------------------------------------------------------------------

    private void switchView(boolean folderView) {
        isFolderTreeActive = folderView;
        ((java.awt.CardLayout) cardPanel.getLayout()).show(cardPanel, folderView ? "folder" : "category");
        updateImportConfigPanel();
    }

    // -------------------------------------------------------------------------
    // Category tree — selection
    // -------------------------------------------------------------------------

    private void onTreeSelect() {
        TreePath path = assetTree.getSelectionPath();
        if (path == null) return;
        Object last = path.getLastPathComponent();
        if (!(last instanceof JCheckBoxTreeNode node) || !node.isLeaf()) return;

        StringBuilder sb = new StringBuilder();
        Object[] components = path.getPath();
        for (int i = 2; i < components.length; i++) {
            sb.append(components[i].toString().replaceAll("\\s*\\(.*?\\)$", "").trim());
            if (i < components.length - 1) sb.append("/");
        }
        if (selectionListener != null && modelsFolder != null)
            selectionListener.onAssetSelected(new File(modelsFolder, sb.toString()));
    }

    private void notifyFocusedRowChange(int row) {
        TreePath tp = assetTree.getPathForRow(row);
        if (tp == null) return;
        Object last = tp.getLastPathComponent();
        if (!(last instanceof JCheckBoxTreeNode node)) return;
        if (node.isLeaf()) {
            if (selectionListener == null || modelsFolder == null) return;
            TreeNodeData data = (TreeNodeData) node.getUserObject();
            selectionListener.onAssetSelected(
                    new File(modelsFolder, stripCategoryPrefix(data.relativePath())));
        } else {
            if (folderListener != null)
                folderListener.onFolderSelected(collectTextureFilesUnder(node));
        }
    }

    private static String stripCategoryPrefix(String rel) {
        int slash = rel.indexOf('/');
        return slash >= 0 ? rel.substring(slash + 1) : rel;
    }

    /** Converts relative texture paths from the category tree to File objects. */
    private List<File> collectTextureFilesUnder(JCheckBoxTreeNode node) {
        if (modelsFolder == null) return List.of();
        List<String> relPaths = collectTexturePathsUnder(node);
        List<File> files = new ArrayList<>(relPaths.size());
        for (String rel : relPaths) {
            File f = new File(modelsFolder, rel);
            if (f.exists()) files.add(f);
        }
        return files;
    }

    // -------------------------------------------------------------------------
    // Category tree — building
    // -------------------------------------------------------------------------

    private JCheckBoxTreeNode buildCategoryTree(String label, List<String> filePaths,
                                                 Map<String, Long> fileSizes) {
        FolderNode folderRoot = new FolderNode(label);
        for (String rel : filePaths) {
            String[] parts = rel.split("/");
            FolderNode cur = folderRoot;
            for (int i = 0; i < parts.length; i++) {
                boolean leaf = i == parts.length - 1;
                cur = cur.getChildOrCreate(parts[i], leaf);
                if (leaf) {
                    cur.incrementFileCount();
                    Long size = fileSizes.get(rel);
                    if (size != null) cur.addSize(size);
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
        if (!fn.isFile() && !fn.getChildren().isEmpty()) {
            node.add(new JCheckBoxTreeNode(SENTINEL, false));
            pendingNodes.put(node, fn);
        }
        return node;
    }

    private void materializeChildren(JCheckBoxTreeNode parent, FolderNode fn) {
        parent.removeAllChildren();
        String parentPath = ((TreeNodeData) parent.getUserObject()).relativePath();
        for (FolderNode child : fn.getChildren().values()) {
            parent.add(toCheckBoxNode(child, parentPath));
        }
        treeModel.reload(parent);
    }

    // -------------------------------------------------------------------------
    // Category tree — lazy expansion and Ctrl+expand
    // -------------------------------------------------------------------------

    private void setupLazyExpansion() {
        assetTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                Object last = event.getPath().getLastPathComponent();
                if (!(last instanceof JCheckBoxTreeNode parent)) return;
                FolderNode fn = pendingNodes.remove(parent);
                if (fn != null) {
                    materializeChildren(parent, fn);
                    if (parent.isChecked()) {
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            assetTree.checkSubTree(
                                    event.getPath().pathByAddingChild(parent.getChildAt(i)), true);
                        }
                    }
                }
            }
            @Override public void treeWillCollapse(TreeExpansionEvent event) {}
        });
    }

    private void setupExpandCollapseBehavior() {
        assetTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                controlDown = e.isControlDown();
                int current  = assetTree.getFocusedRow();
                int rowCount = assetTree.getRowCount();
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (rowCount > 0) {
                        int next = current <= 0 ? 0 : current - 1;
                        assetTree.setFocusedRow(next);
                        notifyFocusedRowChange(next);
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (rowCount > 0) {
                        int next = current < 0 ? 0 : Math.min(current + 1, rowCount - 1);
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
        Object last = path.getLastPathComponent();
        if (last instanceof JCheckBoxTreeNode cbNode) {
            if (isSentinel(cbNode)) return;
            FolderNode fn = pendingNodes.remove(cbNode);
            if (fn != null) materializeChildren(cbNode, fn);
        }
        TreeNode node = (TreeNode) last;
        for (int i = 0; i < node.getChildCount(); i++) {
            expandAllChildren(path.pathByAddingChild(node.getChildAt(i)), expand);
        }
        if (expand) assetTree.expandPath(path);
        else        assetTree.collapsePath(path);
    }

    // -------------------------------------------------------------------------
    // Category tree — collect checked / MDX / textures
    // -------------------------------------------------------------------------

    private void collectChecked(TreeNode treeNode, Set<Path> result) {
        if (!(treeNode instanceof JCheckBoxTreeNode cbNode)) return;
        if (isSentinel(cbNode)) return;
        if (cbNode.isLeaf() && cbNode.isChecked()) {
            TreeNodeData data = (TreeNodeData) cbNode.getUserObject();
            if (data.isFile() && modelsFolder != null) {
                result.add(modelsFolder.toPath()
                        .resolve(stripCategoryPrefix(data.relativePath())).normalize());
            }
            return;
        }
        FolderNode pending = pendingNodes.get(cbNode);
        if (pending != null) {
            if (cbNode.isChecked()) {
                collectCheckedFromFolderNode(
                        pending, ((TreeNodeData) cbNode.getUserObject()).relativePath(), result);
            }
            return;
        }
        for (int i = 0; i < treeNode.getChildCount(); i++) collectChecked(treeNode.getChildAt(i), result);
    }

    private void collectCheckedFromFolderNode(FolderNode fn, String currentPath, Set<Path> result) {
        if (fn.isFile()) {
            if (modelsFolder == null) return;
            result.add(modelsFolder.toPath().resolve(stripCategoryPrefix(currentPath)).normalize());
            return;
        }
        for (FolderNode child : fn.getChildren().values())
            collectCheckedFromFolderNode(child, currentPath + "/" + child.getName(), result);
    }

    private void updateImportConfigPanel() {
        if (importConfigPanel == null) return;
        List<String> mdxFilenames = isFolderTreeActive
                ? collectMdxFilenamesFromFolderTree()
                : collectCheckedMdxFilenames();
        importConfigPanel.setSelectedMdxFilenames(mdxFilenames);
    }

    private List<String> collectCheckedMdxFilenames() {
        List<String> result = new ArrayList<>();
        collectMdxFilenamesRecursive((TreeNode) treeModel.getRoot(), result);
        return result;
    }

    private void collectMdxFilenamesRecursive(TreeNode treeNode, List<String> result) {
        if (!(treeNode instanceof JCheckBoxTreeNode cbNode)) return;
        if (isSentinel(cbNode)) return;
        if (cbNode.isLeaf() && cbNode.isChecked()) {
            TreeNodeData data = (TreeNodeData) cbNode.getUserObject();
            if (data.isFile()) {
                String rel = stripCategoryPrefix(data.relativePath());
                if (rel.toLowerCase().endsWith(".mdx")) result.add(rel);
            }
            return;
        }
        FolderNode pending = pendingNodes.get(cbNode);
        if (pending != null) {
            if (cbNode.isChecked())
                collectMdxFromFolderNode(pending, ((TreeNodeData) cbNode.getUserObject()).relativePath(), result);
            return;
        }
        for (int i = 0; i < treeNode.getChildCount(); i++)
            collectMdxFilenamesRecursive(treeNode.getChildAt(i), result);
    }

    private void collectMdxFromFolderNode(FolderNode fn, String currentPath, List<String> result) {
        if (fn.isFile()) {
            String rel = stripCategoryPrefix(currentPath);
            if (rel.toLowerCase().endsWith(".mdx")) result.add(rel);
            return;
        }
        for (FolderNode child : fn.getChildren().values())
            collectMdxFromFolderNode(child, currentPath + "/" + child.getName(), result);
    }

    private List<String> collectTexturePathsUnder(JCheckBoxTreeNode node) {
        List<String> result = new ArrayList<>();
        collectTexturePathsRecursive(node, result);
        return result;
    }

    private static final Set<String> TEXTURE_EXTENSIONS = new java.util.HashSet<>(
            Arrays.asList(".blp", ".dds", ".tga", ".png", ".jpg", ".jpeg", ".bmp", ".gif"));

    private void collectTexturePathsRecursive(JCheckBoxTreeNode node, List<String> result) {
        if (isSentinel(node)) return;
        if (node.isLeaf()) {
            TreeNodeData data = (TreeNodeData) node.getUserObject();
            if (data.isFile()) {
                String rel = stripCategoryPrefix(data.relativePath());
                int dot = rel.lastIndexOf('.');
                if (dot >= 0 && TEXTURE_EXTENSIONS.contains(rel.substring(dot).toLowerCase()))
                    result.add(rel);
            }
            return;
        }
        FolderNode pending = pendingNodes.get(node);
        if (pending != null) {
            collectTexturePathsFromFolderNode(
                    pending, ((TreeNodeData) node.getUserObject()).relativePath(), result);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof JCheckBoxTreeNode cb) collectTexturePathsRecursive(cb, result);
        }
    }

    private void collectTexturePathsFromFolderNode(FolderNode fn, String currentPath, List<String> result) {
        if (fn.isFile()) {
            String rel = stripCategoryPrefix(currentPath);
            int dot = rel.lastIndexOf('.');
            if (dot >= 0 && TEXTURE_EXTENSIONS.contains(rel.substring(dot).toLowerCase()))
                result.add(rel);
            return;
        }
        for (FolderNode child : fn.getChildren().values())
            collectTexturePathsFromFolderNode(child, currentPath + "/" + child.getName(), result);
    }

    // -------------------------------------------------------------------------
    // Filesystem (folder) tree
    // -------------------------------------------------------------------------

    /** Node user-object for the folder tree: displays name, file count and size. */
    private record FileNodeData(File file, int fileCount, long totalSize) {
        @Override public String toString() {
            if (file.isDirectory()) {
                return file.getName() + " (" + fileCount + " " + Messages.get("tree.files") + ", " + formatSize(totalSize) + ")";
            }
            return file.getName() + " [" + formatSize(totalSize) + "]";
        }
        private static String formatSize(long size) {
            if (size >= 1 << 20) return String.format("%.1f %s", size / 1024.0 / 1024, Messages.get("tree.size.mb"));
            if (size >= 1 << 10) return String.format("%.1f %s", size / 1024.0,          Messages.get("tree.size.kb"));
            return size + " " + Messages.get("tree.size.b");
        }
    }

    /** Recursively counts files and sums sizes under {@code dir}. Returns {fileCount, totalSize}. */
    private static long[] computeDirStats(File dir) {
        long count = 0, size = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isFile()) {
                    count++;
                    size += child.length();
                } else if (child.isDirectory()) {
                    long[] sub = computeDirStats(child);
                    count += sub[0];
                    size += sub[1];
                }
            }
        }
        return new long[]{count, size};
    }

    private void rebuildFolderTree() {
        pendingFolderDirs.clear();
        // Materialize root's children immediately — JTree auto-expands the root via
        // expandedState (bypassing TreeWillExpandListener), so any sentinel on the root
        // would become visible without being replaced.  Subdirectories still use lazy expansion.
        long[] rootStats = computeDirStats(modelsFolder);
        JCheckBoxTreeNode root = new JCheckBoxTreeNode(
                new FileNodeData(modelsFolder, (int) rootStats[0], rootStats[1]), false);
        File[] children = modelsFolder.listFiles();
        if (children != null) {
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File child : children) root.add(buildFolderTreeNode(child));
        }
        folderTreeModel.setRoot(root);
        folderTreeModel.reload();
        folderTree.resetCheckingState();
    }

    private JCheckBoxTreeNode buildFolderTreeNode(File file) {
        FileNodeData data;
        if (file.isDirectory()) {
            long[] stats = computeDirStats(file);
            data = new FileNodeData(file, (int) stats[0], stats[1]);
        } else {
            data = new FileNodeData(file, 0, file.length());
        }
        JCheckBoxTreeNode node = new JCheckBoxTreeNode(data, false);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null && children.length > 0) {
                node.add(new JCheckBoxTreeNode(SENTINEL, false));
                pendingFolderDirs.put(node, file);
            }
        }
        return node;
    }

    private void materializeFolderChildren(JCheckBoxTreeNode parent, File dir) {
        parent.removeAllChildren();
        File[] children = dir.listFiles();
        if (children != null) {
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File child : children) parent.add(buildFolderTreeNode(child));
        }
        folderTreeModel.reload(parent);
    }

    private void setupFolderTreeLazyExpansion() {
        folderTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                Object last = event.getPath().getLastPathComponent();
                if (!(last instanceof JCheckBoxTreeNode parent)) return;
                File dir = pendingFolderDirs.remove(parent);
                if (dir != null) {
                    materializeFolderChildren(parent, dir);
                    if (parent.isChecked()) {
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            folderTree.checkSubTree(
                                    event.getPath().pathByAddingChild(parent.getChildAt(i)), true);
                        }
                    }
                }
            }
            @Override public void treeWillCollapse(TreeExpansionEvent event) {}
        });
    }

    private void onFolderTreeSelect() {
        TreePath path = folderTree.getSelectionPath();
        if (path == null) return;
        Object last = path.getLastPathComponent();
        if (!(last instanceof JCheckBoxTreeNode node)) return;
        if (!(node.getUserObject() instanceof FileNodeData fnd)) return;
        File file = fnd.file();
        if (file.isFile() && selectionListener != null)
            selectionListener.onAssetSelected(file);
    }

    private void notifyFolderTreeFocusedRowChange(int row) {
        TreePath tp = folderTree.getPathForRow(row);
        if (tp == null) return;
        Object last = tp.getLastPathComponent();
        if (!(last instanceof JCheckBoxTreeNode node)) return;
        if (!(node.getUserObject() instanceof FileNodeData fnd)) return;
        File file = fnd.file();
        if (file.isFile()) {
            if (selectionListener != null) selectionListener.onAssetSelected(file);
        } else {
            if (folderListener != null)
                folderListener.onFolderSelected(collectTextureFilesFromFolderTreeNode(node));
        }
    }

    // ---- Folder tree — texture collection ----

    private List<File> collectTextureFilesFromFolderTreeNode(JCheckBoxTreeNode node) {
        List<File> result = new ArrayList<>();
        collectTextureFilesFromFolderTree(node, result);
        return result;
    }

    private void collectTextureFilesFromFolderTree(JCheckBoxTreeNode node, List<File> result) {
        if (isSentinel(node)) return;
        if (!(node.getUserObject() instanceof FileNodeData fnd)) return;
        File file = fnd.file();
        if (file.isFile()) {
            int dot = file.getName().lastIndexOf('.');
            if (dot >= 0 && TEXTURE_EXTENSIONS.contains(file.getName().substring(dot).toLowerCase()))
                result.add(file);
            return;
        }
        File pendingDir = pendingFolderDirs.get(node);
        if (pendingDir != null) {
            collectTextureFilesFromDir(pendingDir, result);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof JCheckBoxTreeNode cb)
                collectTextureFilesFromFolderTree(cb, result);
        }
    }

    private void collectTextureFilesFromDir(File dir, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile()) {
                int dot = child.getName().lastIndexOf('.');
                if (dot >= 0 && TEXTURE_EXTENSIONS.contains(child.getName().substring(dot).toLowerCase()))
                    result.add(child);
            } else if (child.isDirectory()) {
                collectTextureFilesFromDir(child, result);
            }
        }
    }

    // ---- Folder tree — checked files ----

    private Set<Path> getCheckedFilesFromFolderTree() {
        Set<Path> result = new LinkedHashSet<>();
        collectCheckedFolderTree((TreeNode) folderTreeModel.getRoot(), result);
        return result;
    }

    private void collectCheckedFolderTree(TreeNode treeNode, Set<Path> result) {
        if (!(treeNode instanceof JCheckBoxTreeNode cbNode)) return;
        if (isSentinel(cbNode)) return;
        if (!(cbNode.getUserObject() instanceof FileNodeData fnd)) return;
        File file = fnd.file();
        if (file.isFile()) {
            if (cbNode.isChecked()) result.add(file.toPath().normalize());
            return;
        }
        File pendingDir = pendingFolderDirs.get(cbNode);
        if (pendingDir != null) {
            if (cbNode.isChecked()) collectAllFilesFromDir(pendingDir, result);
            return;
        }
        for (int i = 0; i < treeNode.getChildCount(); i++)
            collectCheckedFolderTree(treeNode.getChildAt(i), result);
    }

    private void collectAllFilesFromDir(File dir, Set<Path> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile())            result.add(child.toPath().normalize());
            else if (child.isDirectory()) collectAllFilesFromDir(child, result);
        }
    }

    // ---- Folder tree — MDX filenames for naming preview ----

    private List<String> collectMdxFilenamesFromFolderTree() {
        List<String> result = new ArrayList<>();
        collectMdxFolderTree((TreeNode) folderTreeModel.getRoot(), result);
        return result;
    }

    private void collectMdxFolderTree(TreeNode treeNode, List<String> result) {
        if (!(treeNode instanceof JCheckBoxTreeNode cbNode)) return;
        if (isSentinel(cbNode)) return;
        if (!(cbNode.getUserObject() instanceof FileNodeData fnd)) return;
        File file = fnd.file();
        if (file.isFile()) {
            if (cbNode.isChecked() && file.getName().toLowerCase().endsWith(".mdx"))
                result.add(file.getName());
            return;
        }
        File pendingDir = pendingFolderDirs.get(cbNode);
        if (pendingDir != null) {
            if (cbNode.isChecked()) collectAllMdxFromDir(pendingDir, result);
            return;
        }
        for (int i = 0; i < treeNode.getChildCount(); i++)
            collectMdxFolderTree(treeNode.getChildAt(i), result);
    }

    private void collectAllMdxFromDir(File dir, List<String> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile() && child.getName().toLowerCase().endsWith(".mdx"))
                result.add(child.getName());
            else if (child.isDirectory())
                collectAllMdxFromDir(child, result);
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private static boolean isSentinel(JCheckBoxTreeNode node) {
        return SENTINEL.equals(node.getUserObject());
    }

    // -------------------------------------------------------------------------
    // FolderNode — category tree building helper
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

        String getName()  { return name; }
        boolean isFile()  { return isFile; }
        int getFileCount(){ return fileCount; }
        long getTotalSize(){ return totalSize; }
        Map<String, FolderNode> getChildren() { return children; }
        void incrementFileCount() { fileCount++; }
        void addSize(long size)   { totalSize += size; }

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
