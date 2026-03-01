package org.example.gui;

import org.example.core.model.AssetDiscoveryResult;
import org.example.core.model.ImportOptions;
import org.example.gui.MapPreviewPanel;
import org.example.core.model.MapMetadata;
import org.example.core.model.UnitEntry;
import org.example.core.service.AssetDiscoveryService;
import org.example.core.service.ImportService;
import org.example.core.service.MapMetadataService;
import org.example.gui.i18n.Messages;
import org.example.gui.settings.AppearanceConfig;
import org.example.gui.settings.SettingsDialog;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger(MainFrame.class.getName());

    // ---- Core services ----
    private final MapMetadataService metadataService = new MapMetadataService();
    private final AssetDiscoveryService discoveryService = new AssetDiscoveryService();
    private final ImportService importService = new ImportService();

    // ---- Appearance ----
    private final AppearanceConfig appearanceConfig;

    // ---- State ----
    private File mapFile;
    private File modelsFolder;
    private AssetDiscoveryResult discoveredAssets;
    private SwingWorker<AssetDiscoveryResult, Void> discoveryWorker;

    // ---- Last-used directories for file choosers ----
    private File lastMapDir     = new File("src/test-sample");
    private File lastModelsDir  = new File("src/test-sample");

    // ---- Swing components ----
    private JFrame frame;
    private JTextArea logArea;
    private MapDescriptionPanel mapDescriptionPanel;
    private AssetTreePanel assetTreePanel;
    private PreviewPanel previewPanel;
    private ImportConfigPanel importConfigPanel;

    // Toolbar buttons (kept as fields so applyI18n() can update their labels)
    private JButton openMapButton;
    private JButton importModelsButton;
    private JButton processButton;
    private JButton settingsButton;
    private JButton helpButton;

    // Panels with updatable titles
    private JTabbedPane tabbedPane;
    private JPanel mapDescPanel;
    private JPanel logPanel;

    // Status bar
    private JProgressBar statusBar;

    public MainFrame(AppearanceConfig appearanceConfig) {
        this.appearanceConfig = appearanceConfig;
        checkBlpSupport();
        initialize();
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    private static void checkBlpSupport() {
        LOG.fine("Checking BLP ImageIO support");
        Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("blp");
        if (!readers.hasNext()) {
            LOG.warning("No ImageReader found for BLP format — texture previews may not work");
            System.out.println("Warning: no ImageReader for BLP found.");
        } else {
            LOG.fine("BLP ImageReader found");
        }
    }

    // -------------------------------------------------------------------------
    // Named actions + InputMap / ActionMap
    // -------------------------------------------------------------------------

    /**
     * Derives the default output path: same directory as source, name + "_processed".
     */
    private static String defaultOutputPath(File source) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String ext = dot >= 0 ? name.substring(dot) : "";
        return new File(source.getParentFile(), base + "_processed" + ext).getAbsolutePath();
    }

    private void initialize() {
        frame = new JFrame(Messages.get("app.title"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 900);
        frame.setLocationRelativeTo(null);

        // ---- Toolbar ----
        openMapButton = new JButton(Messages.get("button.openMap"));
        importModelsButton = new JButton(Messages.get("button.importModels"));
        processButton = new JButton(Messages.get("button.process"));
        settingsButton = new JButton(Messages.get("button.settings"));
        helpButton = new JButton(Messages.get("button.help"));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(openMapButton);
        buttonPanel.add(importModelsButton);
        buttonPanel.add(processButton);
        buttonPanel.add(settingsButton);
        buttonPanel.add(helpButton);

        // ---- Status bar ----
        statusBar = new JProgressBar(0, 100);
        statusBar.setStringPainted(true);
        statusBar.setString(Messages.get("status.ready"));

        // ---- Panels ----
        logArea = new JTextArea();
        logArea.setEditable(false);

        mapDescriptionPanel = new MapDescriptionPanel();
        assetTreePanel = new AssetTreePanel();
        previewPanel = new PreviewPanel();
        importConfigPanel = new ImportConfigPanel();

        assetTreePanel.setAssetSelectionListener(this::onAssetSelected);
        assetTreePanel.setFolderSelectionListener(this::onFolderSelected);
        assetTreePanel.setImportConfigPanel(importConfigPanel);

        // "Assets" tab: asset tree on the left, preview panel on the right
        JSplitPane assetPreviewPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, assetTreePanel, previewPanel);
        assetPreviewPane.setResizeWeight(0.6);
        assetPreviewPane.setDividerLocation(350);
        assetPreviewPane.setOneTouchExpandable(true);

        // Log panel with titled border
        logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder(Messages.get("tab.logs")));
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Tabbed pane — Map Description is always visible above, so only 3 tabs here
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab(Messages.get("tab.assets"), assetPreviewPane);
        tabbedPane.addTab(Messages.get("tab.importConfig"), importConfigPanel);
        tabbedPane.addTab(Messages.get("tab.logs"), logPanel);

        // Wrap Map Description with a titled border
        mapDescPanel = new JPanel(new BorderLayout());
        mapDescPanel.setBorder(BorderFactory.createTitledBorder(Messages.get("panel.mapDescription")));
        mapDescPanel.add(mapDescriptionPanel, BorderLayout.CENTER);
        mapDescPanel.setMinimumSize(new Dimension(0, 200)); // allow the divider to be dragged up

        // Map Description sits permanently above the tab pane
        JSplitPane verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, mapDescPanel, tabbedPane);
        verticalSplit.setResizeWeight(0.0);   // tab pane absorbs extra space on window resize
        verticalSplit.setOneTouchExpandable(true);
        verticalSplit.setDividerLocation(200); // sensible default height for map metadata

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(verticalSplit, BorderLayout.CENTER);
        panel.add(statusBar, BorderLayout.SOUTH);

        frame.getContentPane().add(panel);

        // ---- Button wiring ----
        openMapButton.addActionListener(this::onOpenMap);
        importModelsButton.addActionListener(this::onImportModels);
        processButton.addActionListener(this::onProcess);
        settingsButton.addActionListener(this::onOpenSettings);
        helpButton.addActionListener(e -> new HelpDialog(frame).setVisible(true));

        frame.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Button handlers
    // -------------------------------------------------------------------------

    private void onOpenMap(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(lastMapDir);
        chooser.setDialogTitle(Messages.get("dialog.openMap"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;

        mapFile = chooser.getSelectedFile();
        lastMapDir = mapFile.getParentFile();
        LOG.info("Opening map: " + mapFile.getAbsolutePath());
        log(MessageFormat.format(Messages.get("log.selectedMap"), mapFile.getAbsolutePath()));
        importConfigPanel.setOutputPath(defaultOutputPath(mapFile));

        try {
            MapMetadata meta = metadataService.loadMetadata(mapFile);
            LOG.fine("Metadata loaded — name='" + meta.name() + "' warning=" + meta.loadWarning());

            // Warn (but don't abort) when metadata was only partially read,
            // e.g. a Reforged map with an unsupported W3I format version.
            if (meta.loadWarning() != null) {
                log("Warning: " + meta.loadWarning());
            }

            mapDescriptionPanel.setMapName(meta.name());
            mapDescriptionPanel.setDescription(meta.description());
            mapDescriptionPanel.setAuthor(meta.author());
            mapDescriptionPanel.setMapVersion(meta.gameVersion());
            mapDescriptionPanel.setEditorVersion(meta.editorVersion());
            mapDescriptionPanel.setPreviewImage(meta.previewImageBytes());

            // Feed the map image into the Import Configuration preview panel
            byte[] previewBytes = meta.previewImageBytes();
            if (previewBytes != null && previewBytes.length > 0) {
                try {
                    BufferedImage previewImg = ImageIO.read(new ByteArrayInputStream(previewBytes));
                    if (previewImg != null) importConfigPanel.setMapPreviewImage(previewImg);
                } catch (Exception ignored) {
                }
            }

            // Only log camera bounds when the W3I was fully parsed
            if (meta.loadWarning() == null) {
                log("Top left: " + meta.cameraBounds().getTopLeft());
                log("Bottom right: " + meta.cameraBounds().getBottomRight());
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to open map '" + mapFile.getName() + "'", ex);
            log(MessageFormat.format(Messages.get("log.errorLoadingMap"), ex.getMessage()));
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            JTextArea textArea = new JTextArea(
                    "Could not open map file:\n\n" + ex.getMessage()
                            + "\n\nMake sure the file is not open in another application."
                            + "\n\n--- Stack Trace ---\n" + sw);
            textArea.setEditable(false);
            textArea.setCaretPosition(0);
            JScrollPane errScroll = new JScrollPane(textArea);
            errScroll.setPreferredSize(new Dimension(600, 300));
            JOptionPane.showMessageDialog(frame, errScroll, "Map Open Error", JOptionPane.ERROR_MESSAGE);
            mapFile = null; // reset so the user doesn't process a map that failed to load
            return;
        }

        // Load custom unit definitions for the Unit Origin ID suggestion field.
        // Done outside the metadata try-catch so a W3I parse failure doesn't
        // prevent unit suggestions from loading (they use a separate W3U file).
        java.util.List<UnitEntry> mapUnits = metadataService.loadMapUnits(mapFile);
        importConfigPanel.setUnitSuggestions(mapUnits);
        if (!mapUnits.isEmpty()) {
            log("Loaded " + mapUnits.size() + " unit definition(s) for suggestions.");
        }

        // Load existing unit placements from war3mapUnits.doo and show them as
        // yellow triangles on the Import Configuration map preview.
        java.util.List<org.example.core.model.ExistingUnit> existingUnits =
                metadataService.loadExistingUnitPlacements(mapFile);
        importConfigPanel.setExistingUnits(existingUnits);
        if (!existingUnits.isEmpty()) {
            log("Found " + existingUnits.size() + " existing unit placement(s) on map.");
        }
    }

    private void onImportModels(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(lastModelsDir);
        chooser.setDialogTitle(Messages.get("dialog.importFolder"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;

        // Cancel any in-flight scan
        if (discoveryWorker != null && !discoveryWorker.isDone()) discoveryWorker.cancel(true);

        modelsFolder = chooser.getSelectedFile();
        lastModelsDir = modelsFolder.getParentFile();
        LOG.info("Scanning assets folder: " + modelsFolder.getAbsolutePath());
        assetTreePanel.setModelsFolder(modelsFolder);
        log(MessageFormat.format(Messages.get("log.selectedFolder"), modelsFolder.getAbsolutePath()));

        // Disable button and show indeterminate progress while scanning
        importModelsButton.setEnabled(false);
        statusBar.setIndeterminate(true);
        statusBar.setString(Messages.get("status.scanning"));

        File scanFolder = modelsFolder; // capture for background thread
        discoveryWorker = new SwingWorker<>() {
            @Override
            protected AssetDiscoveryResult doInBackground() throws Exception {
                return discoveryService.discover(scanFolder, this::isCancelled);
            }

            @Override
            protected void done() {
                statusBar.setIndeterminate(false);
                importModelsButton.setEnabled(true);
                if (isCancelled()) {
                    statusBar.setString(Messages.get("status.ready"));
                    return;
                }
                try {
                    discoveredAssets = get();
                    LOG.fine("Asset discovery complete: " + discoveredAssets.mdxFiles().size()
                            + " MDX, " + discoveredAssets.textureFiles().size() + " textures");
                    log(MessageFormat.format(Messages.get("log.foundMdx"), discoveredAssets.mdxFiles().size()));
                    log(MessageFormat.format(Messages.get("log.foundTextures"), discoveredAssets.textureFiles().size()));
                    assetTreePanel.updateTree(discoveredAssets.mdxFiles(), discoveredAssets.textureFiles(),
                            discoveredAssets.fileSizes());
                    statusBar.setString(Messages.get("status.ready"));
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Asset discovery failed", ex);
                    log(MessageFormat.format(Messages.get("log.errorReadingFiles"), ex.getMessage()));
                    statusBar.setString(Messages.get("status.error"));
                }
            }
        };
        discoveryWorker.execute();
    }

    // -------------------------------------------------------------------------
    // Asset preview
    // -------------------------------------------------------------------------

    private void onProcess(ActionEvent e) {
        LOG.info("Process triggered: map=" + (mapFile != null ? mapFile.getName() : "null")
                + " folder=" + (modelsFolder != null ? modelsFolder.getName() : "null"));
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
            discoveredAssets.textureFiles().forEach(r ->
                    selectedFiles.add(modelsFolder.toPath().resolve(r).normalize()));
        }

        // Convert GUI enum → core enum by name
        ImportOptions.PlacingOrder placingOrder =
                importConfigPanel.getPlacingOrder() == MapPreviewPanel.PlacingOrder.COLUMNS
                        ? ImportOptions.PlacingOrder.COLUMNS
                        : ImportOptions.PlacingOrder.ROWS;

        ImportOptions opts = new ImportOptions(
                importConfigPanel.isCreateUnitsSelected(),
                importConfigPanel.isPlaceUnitsSelected(),
                importConfigPanel.isClearUnitsSelected(),
                importConfigPanel.isClearAssetsSelected(),
                importConfigPanel.getUnitScaling(),
                importConfigPanel.getUnitAngle(),
                importConfigPanel.getUnitSpacingX(),
                importConfigPanel.getUnitSpacingY(),
                importConfigPanel.getUnitOriginId(),
                importConfigPanel.isAutoNameUnitsEnabled(),
                importConfigPanel.getNameFormat(),
                importConfigPanel.isAutoAssignIconEnabled(),
                importConfigPanel.isFlattenPathsEnabled(),
                placingOrder,
                importConfigPanel.getPlacementBounds()  // null when no shape drawn → camera-bounds fallback
        );

        // Resolve the output file path
        String outPath = importConfigPanel.getOutputPath();
        if (outPath.isEmpty()) {
            outPath = defaultOutputPath(mapFile);
            importConfigPanel.setOutputPath(outPath);
        }
        File outputFile = new File(outPath);

        // Update status bar and disable the process button while running
        statusBar.setValue(0);
        statusBar.setString(Messages.get("status.importing"));
        processButton.setEnabled(false);

        LOG.fine("Launching import task: " + selectedFiles.size() + " file(s) → " + outputFile.getName());

        // Run on background thread via SwingWorker wrapper
        MapProcessingTask task = new MapProcessingTask(
                mapFile, outputFile, selectedFiles, modelsFolder, opts,
                importService, this::log, frame);

        // Progress updates arrive on the EDT via PropertyChangeEvent
        task.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int pct = (Integer) evt.getNewValue();
                statusBar.setValue(pct);
                if (pct >= 100) {
                    statusBar.setString(Messages.get("status.done"));
                    processButton.setEnabled(true);
                } else {
                    statusBar.setString(MessageFormat.format(Messages.get("status.importingPct"), pct));
                }
            } else if (SwingWorker.StateValue.DONE == evt.getNewValue()) {
                // Covers failure path where done() shows the popup
                processButton.setEnabled(true);
                if (statusBar.getValue() < 100) {
                    statusBar.setValue(0);
                    statusBar.setString(Messages.get("status.error"));
                }
            }
        });

        task.execute();
    }

    private void onOpenSettings(ActionEvent e) {
        LOG.fine("Opening settings dialog");
        SettingsDialog dialog = new SettingsDialog(frame, appearanceConfig);
        dialog.setLocaleChangeListener(locale -> applyI18n());
        dialog.setLookAndFeelChangeListener(() -> {
            SwingUtilities.updateComponentTreeUI(frame);
            // Do NOT pack() — that would resize the main window to its preferred size.
            // updateComponentTreeUI already repaints every component in-place.
        });
        dialog.setVisible(true);
    }

    private void onAssetSelected(File file) {
        if (file.exists() && file.isFile()) {
            previewPanel.setImage(file);
        }
    }

    // -------------------------------------------------------------------------
    // i18n refresh
    // -------------------------------------------------------------------------

    private void onFolderSelected(List<File> textureFiles) {
        previewPanel.setImages(textureFiles);
    }

    // -------------------------------------------------------------------------
    // Utilities
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
        helpButton.setText(Messages.get("button.help"));
        // Tab titles
        tabbedPane.setTitleAt(0, Messages.get("tab.assets"));
        tabbedPane.setTitleAt(1, Messages.get("tab.importConfig"));
        tabbedPane.setTitleAt(2, Messages.get("tab.logs"));
        // Panel titled borders — setBorder() triggers revalidate+repaint automatically
        mapDescPanel.setBorder(BorderFactory.createTitledBorder(Messages.get("panel.mapDescription")));
        logPanel.setBorder(BorderFactory.createTitledBorder(Messages.get("tab.logs")));
        // Status bar — reset to Ready (safe: no import runs while settings are open)
        statusBar.setString(Messages.get("status.ready"));
        // Child panels
        mapDescriptionPanel.applyI18n();
        assetTreePanel.applyI18n();
        previewPanel.applyI18n();
        importConfigPanel.applyI18n();
        frame.repaint();
    }

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
