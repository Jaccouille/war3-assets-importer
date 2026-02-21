package org.example;

import net.moonlightflower.wc3libs.bin.app.W3I;
import net.moonlightflower.wc3libs.txt.WTS;
import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class MainFrame {
    private final java.util.List<String> mdxFiles = new ArrayList<String>();
    private final java.util.List<String> blpFiles = new ArrayList<String>();
    private JFrame frame;
    private JTextArea logArea;
    private File mapFile;
    private File modelsFolder;
    private MapOptionsPanel optionsPanel;
    private AssetTreePanel assetTreePanel;
    private PreviewPanel previewPanel;

    public MainFrame() {
        // This method is not needed anymore, as we are using createAndShowGUI()
        // to set up the main frame and its components.
        initialize();
    }

    private void initialize() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("blp");
        if (!readers.hasNext()) {
            System.out.println("No ImageReader for BLP found.");
        } else {
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                System.out.println("Found BLP ImageReader: " + reader.getClass().getName());
            }
        }

        String[] formats = ImageIO.getReaderFileSuffixes();
        System.out.println("Registered ImageIO formats:");
        for (String format : formats) {
            System.out.println(" - " + format);
        }

        frame = new JFrame("Warcraft 3 Model Importer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 720);
        frame.setLocationRelativeTo(null); // Center the window

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Buttons Panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());

        JButton openMapButton = new JButton("Open Map");
        JButton importModelsButton = new JButton("Import Models Folder");
        JButton processButton = new JButton("Process and Save");

        buttonPanel.add(openMapButton);
        buttonPanel.add(importModelsButton);
        buttonPanel.add(processButton);

        // Log Area
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // Asset tree (right side)
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Model Assets");

        optionsPanel = new MapOptionsPanel();
        assetTreePanel = new AssetTreePanel();
        previewPanel = new PreviewPanel();

        // This is the callback that gets triggered when a file is selected in the tree.
        // You can now construct the full path and send it to the preview panel.
        assetTreePanel.setAssetSelectionListener(this::displayPreviewAsset);

        JSplitPane assetPreviewPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, assetTreePanel, previewPanel);
        assetPreviewPane.setResizeWeight(0.7); // Adjust as needed
        assetPreviewPane.setDividerLocation(400);
        assetPreviewPane.setOneTouchExpandable(true);

        JSplitPane leftRightPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, optionsPanel, assetPreviewPane);
        leftRightPane.setResizeWeight(0.3); // Adjust to give more space to asset/preview area
        leftRightPane.setDividerLocation(300);
        leftRightPane.setOneTouchExpandable(true);

        JScrollPane logScrollPane = new JScrollPane(logArea);
//        logScrollPane.setPreferredSize(new Dimension(600, 100));

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, leftRightPane, logScrollPane);
        verticalSplit.setResizeWeight(0.8);
        verticalSplit.setDividerLocation(300);
        verticalSplit.setOneTouchExpandable(true);

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(verticalSplit, BorderLayout.CENTER);

        frame.getContentPane().add(panel);
        frame.setVisible(true);

        // Button Actions
        openMapButton.addActionListener(this::onOpenMap);
        importModelsButton.addActionListener(this::onImportModels);
        processButton.addActionListener(this::onProcess);
    }

    private void displayPreviewAsset(String relativePath) {
        if (modelsFolder != null) {
            File selectedFile = new File(modelsFolder, relativePath);
            if (selectedFile.exists() && selectedFile.isFile()) {
                try {
                    BufferedImage image = ImageIO.read(selectedFile);
                    if (image != null) {
                        System.out.println("BLP file loaded: " + image.getWidth() + "x" + image.getHeight());
                    } else {
                        System.out.println("ImageIO.read returned null for the BLP file.");
                    }
                    previewPanel.setImage(image); // assuming you have a setImage(BufferedImage) method
                } catch (Exception ex) {
                    ex.printStackTrace();
                    log("Failed to load image: " + selectedFile.getPath());
                }
            } else {
                log("Selected file doesn't exist: " + selectedFile.getPath());
            }
        }
    }

    private void onOpenMap(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser(new File("src/test-sample"));
        fileChooser.setDialogTitle("Select Warcraft 3 Map (.w3x/.w3m)");
        int result = fileChooser.showOpenDialog(frame);

        if (result != JFileChooser.APPROVE_OPTION) return;

        mapFile = fileChooser.getSelectedFile();
        log("Selected map: " + mapFile.getAbsolutePath());

        try (JMpqEditor mpqEditor = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {
            displayMapInfo(mpqEditor);
        } catch (Exception ex) {
            log("Error loading map: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void displayMapInfo(JMpqEditor mpqEditor) throws Exception {
        W3I w3i = new W3I(mpqEditor.extractFileAsBytes("war3map.w3i"));
        WTS wts = new WTS(new ByteArrayInputStream(mpqEditor.extractFileAsBytes("war3map.wts")));

        Map<String, String> namedEntries = wts.getNamedEntries();
        byte[] bytes = mpqEditor.extractFileAsBytes("war3mapMap.blp");

        String gameVersion = StringUtils.buildGameVersionInfo(w3i);
        String editorVersion = String.valueOf(w3i.getEditorVersion());
        String name = namedEntries.getOrDefault(w3i.getMapName(), "<unknown>");
        String author = namedEntries.getOrDefault(w3i.getMapAuthor(), "<unknown>");
        String desc = namedEntries.getOrDefault(w3i.getMapDescription(), "<no description>");


        CameraBounds.getInstance().setCameraBounds(
                w3i.getCameraBounds1(),
                w3i.getCameraBounds2(),
                w3i.getCameraBounds3(),
                w3i.getCameraBounds4()
        );

        // top left -> Bottom right
        // bottom left -> top left
        // bottom right -> bottom left
        logArea.append("Top left " + CameraBounds.getInstance().getTopLeft() + "\n");
        logArea.append("Top right " + CameraBounds.getInstance().getTopRight() + "\n");
        logArea.append("Bottom left " + CameraBounds.getInstance().getBottomLeft() + "\n");
        logArea.append("Bottom right " + CameraBounds.getInstance().getBottomRight() + "\n");


        optionsPanel.setMapName(name);
        optionsPanel.setDescription(desc);
        optionsPanel.setAuthor(author);
        optionsPanel.setMapVersion(gameVersion);
        optionsPanel.setEditorVersion(editorVersion);
        optionsPanel.setPreviewImage(bytes);

        String info = String.format(
                "%s - Author: %s\nWidth: %d\nHeight: %d\nPlayers: %d\nDescription: %s",
                name,
                author,
                w3i.getWidth(),
                w3i.getHeight(),
                w3i.getPlayers().size(),
                desc
        );

        logArea.append("Map Info: " + info + "\n");
    }

    private void onImportModels(ActionEvent e) {
        JFileChooser dirChooser = new JFileChooser((new File("src/test-sample")));
        dirChooser.setDialogTitle("Select Folder with Models");
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = dirChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            modelsFolder = dirChooser.getSelectedFile();
            assetTreePanel.setModelsFolder(modelsFolder);
            log("Selected model folder: " + modelsFolder.getAbsolutePath());

            mdxFiles.clear();
            blpFiles.clear();

            try {
                Files.walk(modelsFolder.toPath())
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            String relPath = modelsFolder.toPath().relativize(path).toString().replace("\\", "/");
                            if (relPath.toLowerCase().endsWith(".mdx")) {
                                mdxFiles.add(relPath);
                            } else if (relPath.toLowerCase().endsWith(".blp")) {
                                blpFiles.add(relPath);
                            }
                        });

                log("Found " + mdxFiles.size() + " .mdx files");
                log("Found " + blpFiles.size() + " .blp files");

            } catch (IOException ex) {
                log("Error reading files: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        updateAssetTree();
    }

    private void updateAssetTree() {
        assetTreePanel.updateTree(mdxFiles, blpFiles);
    }

    private void onProcess(ActionEvent e) {
        MapProcessingTask task = new MapProcessingTask(mapFile, assetTreePanel.getCheckedFiles(), this::log);
        task.execute(); // Will run in background
    }

    private void log(String message) {
        logArea.append(message + "\n");
    }
}
