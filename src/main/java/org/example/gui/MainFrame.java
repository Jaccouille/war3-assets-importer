package org.example.gui;

import org.example.core.model.AssetDiscoveryResult;
import org.example.core.model.ImportOptions;
import org.example.core.model.MapMetadata;
import org.example.core.service.AssetDiscoveryService;
import org.example.core.service.ImportService;
import org.example.core.service.MapMetadataService;
import org.example.gui.i18n.Messages;
import org.example.gui.settings.KeybindingsConfig;
import org.example.gui.settings.SettingsDialog;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Main application window.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Lay out the UI panels (options / asset tree / preview / log)</li>
 *   <li>Wire button actions to core services</li>
 *   <li>Apply i18n strings and keybindings</li>
 *   <li>Open the {@link SettingsDialog} and propagate locale/keybinding changes</li>
 * </ul>
 *
 * <p>All business logic lives in {@code core.*} — this class only drives the UI.
 */
public class MainFrame {

    // ---- Core services ----
    private final MapMetadataService metadataService = new MapMetadataService();
    private final AssetDiscoveryService discoveryService = new AssetDiscoveryService();
    private final ImportService importService = new ImportService();

    // ---- Keybindings ----
    private final KeybindingsConfig keybindingsConfig = new KeybindingsConfig();

    // ---- State ----
    private File mapFile;
    private File modelsFolder;
    private AssetDiscoveryResult discoveredAssets;

    // ---- Swing components ----
    private JFrame frame;
    private JTextArea logArea;
    private MapOptionsPanel    optionsPanel;
    private AssetTreePanel     assetTreePanel;
    private PreviewPanel       previewPanel;
    private ImportConfigPanel  importConfigPanel;

    // Toolbar buttons (kept as fields so applyI18n() can update their labels)
    private JButton openMapButton;
    private JButton importModelsButton;
    private JButton processButton;
    private JButton settingsButton;

    public MainFrame() {
        keybindingsConfig.load();
        checkBlpSupport();
        initialize();
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    private void initialize() {
        frame = new JFrame(Messages.get("app.title"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 720);
        frame.setLocationRelativeTo(null);

        // ---- Toolbar ----
        openMapButton = new JButton(Messages.get("button.openMap"));
        importModelsButton = new JButton(Messages.get("button.importModels"));
        processButton = new JButton(Messages.get("button.process"));
        settingsButton = new JButton(Messages.get("button.settings"));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(openMapButton);
        buttonPanel.add(importModelsButton);
        buttonPanel.add(processButton);
        buttonPanel.add(settingsButton);

        // ---- Panels ----
        logArea = new JTextArea();
        logArea.setEditable(false);

        optionsPanel      = new MapOptionsPanel();
        assetTreePanel    = new AssetTreePanel();
        previewPanel      = new PreviewPanel();
        importConfigPanel = new ImportConfigPanel();

        assetTreePanel.setAssetSelectionListener(this::onAssetSelected);
        assetTreePanel.setFolderSelectionListener(this::onFolderSelected);

        // "Assets" tab: asset tree on the left, preview panel on the right
        JSplitPane assetPreviewPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, assetTreePanel, previewPanel);
        assetPreviewPane.setResizeWeight(0.6);
        assetPreviewPane.setDividerLocation(350);
        assetPreviewPane.setOneTouchExpandable(true);

        // Tabbed pane replaces the old horizontal split
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Map Description",      optionsPanel);
        tabbedPane.addTab("Assets",               assetPreviewPane);
        tabbedPane.addTab("Import Configuration", importConfigPanel);

        JSplitPane verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, tabbedPane, new JScrollPane(logArea));
        verticalSplit.setResizeWeight(0.8);
        verticalSplit.setDividerLocation(500);
        verticalSplit.setOneTouchExpandable(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(verticalSplit, BorderLayout.CENTER);

        frame.getContentPane().add(panel);

        // ---- Actions and keybindings ----
        registerActions();
        applyKeybindings();

        // ---- Button wiring ----
        openMapButton.addActionListener(this::onOpenMap);
        importModelsButton.addActionListener(this::onImportModels);
        processButton.addActionListener(this::onProcess);
        settingsButton.addActionListener(this::onOpenSettings);

        frame.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Named actions + InputMap / ActionMap
    // -------------------------------------------------------------------------

    private void registerActions() {
        ActionMap am = frame.getRootPane().getActionMap();
        am.put(KeybindingsConfig.ACTION_OPEN_MAP,
                new AbstractAction() { public void actionPerformed(ActionEvent e) { onOpenMap(e); } });
        am.put(KeybindingsConfig.ACTION_IMPORT_MODELS,
                new AbstractAction() { public void actionPerformed(ActionEvent e) { onImportModels(e); } });
        am.put(KeybindingsConfig.ACTION_PROCESS,
                new AbstractAction() { public void actionPerformed(ActionEvent e) { onProcess(e); } });
        am.put(KeybindingsConfig.ACTION_SETTINGS,
                new AbstractAction() { public void actionPerformed(ActionEvent e) { onOpenSettings(e); } });
    }

    /** (Re-)applies keybindings from config to the root pane InputMap. */
    public void applyKeybindings() {
        InputMap im = frame.getRootPane()
                .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        im.clear();
        for (String action : KeybindingsConfig.DEFAULTS.keySet()) {
            KeyStroke ks = keybindingsConfig.getKeyStroke(action);
            if (ks != null) {
                im.put(ks, action);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Button handlers
    // -------------------------------------------------------------------------

    private void onOpenMap(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(new File("src/test-sample"));
        chooser.setDialogTitle(Messages.get("dialog.openMap"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;

        mapFile = chooser.getSelectedFile();
        log(MessageFormat.format(Messages.get("log.selectedMap"), mapFile.getAbsolutePath()));

        try {
            MapMetadata meta = metadataService.loadMetadata(mapFile);

            // Warn (but don't abort) when metadata was only partially read,
            // e.g. a Reforged map with an unsupported W3I format version.
            if (meta.loadWarning() != null) {
                log("Warning: " + meta.loadWarning());
            }

            optionsPanel.setMapName(meta.name());
            optionsPanel.setDescription(meta.description());
            optionsPanel.setAuthor(meta.author());
            optionsPanel.setMapVersion(meta.gameVersion());
            optionsPanel.setEditorVersion(meta.editorVersion());
            optionsPanel.setPreviewImage(meta.previewImageBytes());

            // Feed the map image into the Import Configuration preview panel
            byte[] previewBytes = meta.previewImageBytes();
            if (previewBytes != null && previewBytes.length > 0) {
                try {
                    BufferedImage previewImg = ImageIO.read(new ByteArrayInputStream(previewBytes));
                    if (previewImg != null) importConfigPanel.setMapPreviewImage(previewImg);
                } catch (Exception ignored) {}
            }

            // Only log camera bounds when the W3I was fully parsed
            if (meta.loadWarning() == null) {
                log("Top left: " + meta.cameraBounds().getTopLeft());
                log("Bottom right: " + meta.cameraBounds().getBottomRight());
            }
        } catch (Exception ex) {
            log(MessageFormat.format(Messages.get("log.errorLoadingMap"), ex.getMessage()));
        }
    }

    private void onImportModels(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(new File("src/test-sample"));
        chooser.setDialogTitle(Messages.get("dialog.importFolder"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;

        modelsFolder = chooser.getSelectedFile();
        assetTreePanel.setModelsFolder(modelsFolder);
        log(MessageFormat.format(Messages.get("log.selectedFolder"), modelsFolder.getAbsolutePath()));

        try {
            discoveredAssets = discoveryService.discover(modelsFolder);
            log(MessageFormat.format(Messages.get("log.foundMdx"), discoveredAssets.mdxFiles().size()));
            log(MessageFormat.format(Messages.get("log.foundBlp"), discoveredAssets.blpFiles().size()));
            assetTreePanel.updateTree(discoveredAssets.mdxFiles(), discoveredAssets.blpFiles());
        } catch (Exception ex) {
            log(MessageFormat.format(Messages.get("log.errorReadingFiles"), ex.getMessage()));
        }
    }

    private void onProcess(ActionEvent e) {
        if (mapFile == null || modelsFolder == null) {
            log("Please select a map and a models folder first.");
            return;
        }

        // Collect selected files from the tree (falls back to all discovered if tree empty)
        Set<Path> selectedFiles = assetTreePanel.getCheckedFiles();
        if (selectedFiles.isEmpty() && discoveredAssets != null) {
            // If nothing is checked, import everything
            discoveredAssets.mdxFiles().forEach(r ->
                    selectedFiles.add(modelsFolder.toPath().resolve(r).normalize()));
            discoveredAssets.blpFiles().forEach(r ->
                    selectedFiles.add(modelsFolder.toPath().resolve(r).normalize()));
        }

        ImportOptions opts = new ImportOptions(
                importConfigPanel.isCreateUnitsSelected(),
                importConfigPanel.isPlaceUnitsSelected(),
                importConfigPanel.isClearUnitsSelected(),
                importConfigPanel.isClearAssetsSelected(),
                "hfoo"  // Default unit definition
        );

        // Run on background thread via SwingWorker wrapper
        MapProcessingTask task = new MapProcessingTask(
                mapFile, selectedFiles, modelsFolder, opts, importService, this::log);
        task.execute();
    }

    private void onOpenSettings(ActionEvent e) {
        SettingsDialog dialog = new SettingsDialog(frame, keybindingsConfig);
        dialog.setLocaleChangeListener(locale -> applyI18n());
        dialog.setVisible(true);
        // After dialog closes, re-apply keybindings in case they changed
        applyKeybindings();
    }

    // -------------------------------------------------------------------------
    // Asset preview
    // -------------------------------------------------------------------------

    private void onAssetSelected(String relativePath) {
        if (modelsFolder == null) return;
        File asset = new File(modelsFolder, relativePath);
        if (asset.exists() && asset.isFile()) {
            previewPanel.setImage(asset);
        }
    }

    private void onFolderSelected(List<String> blpRelativePaths) {
        if (modelsFolder == null) return;
        List<File> files = new ArrayList<>(blpRelativePaths.size());
        for (String rel : blpRelativePaths) {
            File f = new File(modelsFolder, rel);
            if (f.exists()) files.add(f);
        }
        previewPanel.setImages(files);
    }

    // -------------------------------------------------------------------------
    // i18n refresh
    // -------------------------------------------------------------------------

    /**
     * Refreshes all translatable labels after a locale change.
     * Called by {@link SettingsDialog} when the user picks a new language.
     */
    public void applyI18n() {
        frame.setTitle(Messages.get("app.title"));
        openMapButton.setText(Messages.get("button.openMap"));
        importModelsButton.setText(Messages.get("button.importModels"));
        processButton.setText(Messages.get("button.process"));
        settingsButton.setText(Messages.get("button.settings"));
        optionsPanel.applyI18n();
        assetTreePanel.applyI18n();
        previewPanel.applyI18n();
        frame.repaint();
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static void checkBlpSupport() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("blp");
        if (!readers.hasNext()) {
            System.out.println("Warning: no ImageReader for BLP found.");
        }
    }
}
