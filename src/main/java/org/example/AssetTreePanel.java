package org.example;

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
import java.util.*;

public class AssetTreePanel extends JPanel {
    private final JCheckBoxTree assetTree;
    private final DefaultTreeModel treeModel;
    private boolean controlDown = false;
    private boolean isTreeUpdating = false;
    private File modelsFolder;
    private AssetSelectionListener selectionListener;

    public AssetTreePanel() {
        setLayout(new java.awt.BorderLayout());

        TreeNodeData rootData = new TreeNodeData("Model Assets", false, "", 0, 0);
        JCheckBoxTreeNode root = new JCheckBoxTreeNode(rootData, true);
        treeModel = new DefaultTreeModel(root);
        assetTree = new JCheckBoxTree(treeModel);

//        assetTree.setEditable(true);
//        assetTree.setCellRenderer(new CheckBoxNodeRenderer());
//        assetTree.setCellEditor(new CheckBoxNodeEditor());


        JScrollPane treeScrollPane = new JScrollPane(assetTree);
        treeScrollPane.setPreferredSize(new java.awt.Dimension(200, 400));

        add(treeScrollPane, java.awt.BorderLayout.CENTER);

        setupExpandCollapseBehavior();
        assetTree.addTreeSelectionListener(e -> onTreeSelect());
        assetTree.addCheckChangeEventListener(evt -> {
            Object nodeObj = evt.getSource();
            if (!(nodeObj instanceof JCheckBoxTreeNode node)) return;
            if (!node.isLeaf()) return;

            TreeNodeData data = (TreeNodeData) node.getUserObject();
            if (selectionListener != null) {
                selectionListener.onAssetSelected(data.relativePath());
            }
        });

    }

    public void setAssetSelectionListener(AssetSelectionListener listener) {
        this.selectionListener = listener;
    }

    private void onTreeSelect() {
        TreePath path = assetTree.getSelectionPath(); // get selected path
        if (path == null) return;

        Object lastComponent = path.getLastPathComponent();
        if (!(lastComponent instanceof JCheckBoxTreeNode node)) return;

        if (node.isLeaf()) { // only fire when selecting an actual file
            StringBuilder sb = new StringBuilder();
            Object[] components = path.getPath();

            // Skip "Model Assets" and "BLP Files (...)"/"MDX Files (...)"
            for (int i = 2; i < components.length; i++) {
                String part = components[i].toString().replaceAll("\\s*\\(\\d+\\)$", "").trim();
                sb.append(part);
                if (i < components.length - 1) sb.append("/");
            }

            if (selectionListener != null) {
                selectionListener.onAssetSelected(sb.toString());
            }
        }
    }

    public void updateTree(List<String> mdxFiles, List<String> blpFiles) {
        // Build the two sub trees
        JCheckBoxTreeNode mdxNode = buildFolderTree("MDX Files", mdxFiles);
        JCheckBoxTreeNode blpNode = buildFolderTree("BLP Files", blpFiles);

        // Pull their data
        TreeNodeData mdxData = (TreeNodeData) mdxNode.getUserObject();
        TreeNodeData blpData = (TreeNodeData) blpNode.getUserObject();

        // Sum counts and sizes
        int totalFiles = mdxData.fileCount() + blpData.fileCount();
        long totalSize = mdxData.sizeInBytes() + blpData.sizeInBytes();

        // Now build the root with the actual totals
        TreeNodeData rootData = new TreeNodeData(
                "Model Assets",
                false,
                "",           // no meaningful path for the virtual root
                totalSize,
                totalFiles
        );
        JCheckBoxTreeNode root = new JCheckBoxTreeNode(rootData, false);

        // Attach children
        root.add(mdxNode);
        root.add(blpNode);

        // Install into the model
        treeModel.setRoot(root);
        treeModel.reload();

        // Clear any old checks, etc.
        assetTree.resetCheckingState();
    }

    public JTree getTree() {
        return assetTree;
    }

    public void setModelsFolder(File folder) {
        this.modelsFolder = folder;
    }

    private void setupExpandCollapseBehavior() {
        assetTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                controlDown = e.isControlDown();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                controlDown = e.isControlDown();
            }
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
            TreeNode child = node.getChildAt(i);
            TreePath childPath = path.pathByAddingChild(child);
            expandAllChildren(childPath, expand);
        }
        if (expand) {
            assetTree.expandPath(path);
        } else {
            assetTree.collapsePath(path);
        }
    }

    /**
     * Builds a tree structure representing a virtual folder hierarchy from a list of file paths.
     * Each file path (e.g., "units/orc/grunt.mdx") is split into folders and files and used to
     * populate a tree model of nodes. Directories are counted with the number of contained files.
     *
     * @param label     the label for the root node (e.g., "MDX Files" or "BLP Files")
     * @param filePaths list of relative file paths to insert into the tree
     * @return a JCheckBoxTreeNode representing the root of the constructed tree
     */
    private JCheckBoxTreeNode buildFolderTree(String label, List<String> filePaths) {
        // 1) First build a FolderNode hierarchy with counts & sizes
        FolderNode folderRoot = new FolderNode(label);
        for (String rel : filePaths) {
            String[] parts = rel.split("/");
            FolderNode cur = folderRoot;
            for (int i = 0; i < parts.length; i++) {
                boolean leaf = i == parts.length - 1;
                cur = cur.getChildOrCreate(parts[i], leaf);
                if (leaf) {
                    cur.incrementFileCount();
                    // add size
                    try {
                        long size = Files.size(Paths.get(modelsFolder.getAbsolutePath(), rel));
                        cur.addSize(size);
                    } catch (IOException e) { /* ignore */ }
                }
            }
        }
        // roll up counts & sizes
        folderRoot.recalculateCountsAndSizes();

        // 2) Now convert to JCheckBoxTreeNode/TreeNodeData
        return toCheckBoxNode(folderRoot, "");
    }

    /**
     * Recursively convert FolderNode → JCheckBoxTreeNode(TreeNodeData)
     */
    private JCheckBoxTreeNode toCheckBoxNode(FolderNode fn, String parentPath) {
        String fullPath = parentPath.isEmpty() ? fn.getName() : parentPath + "/" + fn.getName();
        TreeNodeData data = new TreeNodeData(
                fn.getName(),
                fn.isFile(),
                fullPath,
                fn.getTotalSize(),
                fn.getFileCount()
        );
        JCheckBoxTreeNode node = new JCheckBoxTreeNode(data, false);
        for (FolderNode child : fn.getChildren().values()) {
            node.add(toCheckBoxNode(child, fullPath));
        }
        return node;
    }

    /**
     * @return the set of Files corresponding to all checked leaf nodes.
     */
    public Set<Path> getCheckedFiles() {
        Set<Path> files = new HashSet<>();
        Path baseFolderPath = modelsFolder.toPath().toAbsolutePath().normalize();
        // Ask the JCheckBoxTree for all checked tree nodes
        TreeNode[] checked = assetTree.getCheckedPaths();
        for (TreeNode tn : checked) {
            if (!(tn instanceof JCheckBoxTreeNode node)) {
                continue;
            }
            // Pull out your data
            Object obj = node.getUserObject();
            if (!(obj instanceof TreeNodeData data)) {
                continue;
            }
            // Only include real files
            if (!data.isFile()) {
                continue;
            }
            // Resolve relative path against the modelsFolder base
            Path filePath = new File(modelsFolder, data.relativePath()).toPath();
            filePath = baseFolderPath.relativize(filePath);
            files.add(filePath);
        }
        return files;
    }

    public interface AssetSelectionListener {
        void onAssetSelected(String relativePath);
    }

    private static class FolderNode {
        private final String name;
        private final boolean isFile;
        //        private final Map<String, FolderNode> children = new LinkedHashMap<>();
        private final Map<String, FolderNode> children = new LinkedHashMap<>();
        private int fileCount = 0;
        private long totalSize = 0;

        public FolderNode(String name, boolean isFile) {
            this.name = name;
            this.isFile = isFile;
        }

        public FolderNode(String name) {
            this(name, false);
        }

        public FolderNode getChildOrCreate(String name, boolean leaf) {
            return children.computeIfAbsent(name, n -> new FolderNode(n, leaf));
        }

        public void addChild(FolderNode child) {
            children.put(child.name, child);
        }

        public FolderNode getChild(String name) {
            return children.get(name);
        }

        public String getName() {
            return name;
        }

        public boolean isFile() {
            return isFile;
        }

        public int getFileCount() {
            return fileCount;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public Map<String, FolderNode> getChildren() {
            return children;
        }

        public void incrementFileCount() {
            fileCount++;
        }

        public void addSize(long size) {
            this.totalSize += size;
        }

        /**
         * Recursively recalculates file counts and sizes.
         */
        public int recalculateCountsAndSizes() {
            if (isFile) return fileCount;

            fileCount = 0;
            totalSize = 0;
            for (FolderNode child : children.values()) {
                fileCount += child.recalculateCountsAndSizes();
                totalSize += child.totalSize;
            }
            return fileCount;
        }

        /**
         * * Recursively converts a FolderNode into a JCheckBoxTreeNode,
         * * using TreeNodeData to hold name, path, size and counts.
         * *
         * * @param node        the FolderNode to convert
         * * @param parentPath  the path built so far (no leading slash for root)
         */
        private JCheckBoxTreeNode toTreeNode(String parentPath) {
            // Build the full relative path for this node
            String fullPath = parentPath.isEmpty()
                    ? this.getName()
                    : parentPath + "/" + this.getName();

            // Create the TreeNodeData payload
            TreeNodeData data = new TreeNodeData(
                    this.getName(),
                    this.isFile(),
                    fullPath,
                    this.getTotalSize(),
                    this.getFileCount()
            );

            // Create the Swing tree node
            JCheckBoxTreeNode treeNode = new JCheckBoxTreeNode(data, true);

            // Recurse for children
            for (FolderNode child : this.getChildren().values()) {
                treeNode.add(child.toTreeNode(fullPath));
            }
            return treeNode;
        }
    }
}
