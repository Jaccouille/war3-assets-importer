package com.hiveworkshop.war3assetsimporter.cli;

import com.hiveworkshop.war3assetsimporter.Main;
import com.hiveworkshop.war3assetsimporter.core.model.AssetDiscoveryResult;
import com.hiveworkshop.war3assetsimporter.core.model.ImportOptions;
import com.hiveworkshop.war3assetsimporter.core.model.ImportResult;
import com.hiveworkshop.war3assetsimporter.core.service.AssetDiscoveryService;
import com.hiveworkshop.war3assetsimporter.core.service.ImportService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * CLI entry point for War3AssetsImporter.
 *
 * <pre>
 * Usage:
 *   war3importer -m map.w3x -f models/ [--create-units] [--place-units] ...
 *   war3importer --help
 * </pre>
 */
@Command(
        name = "war3importer",
        mixinStandardHelpOptions = true,
        version = "war3importer 1.0",
        description = "Imports MDX/BLP assets into a Warcraft 3 map (.w3x/.w3m)."
)
public class ImportCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(ImportCommand.class.getName());

    @Option(
            names = {"-m", "--map"},
            required = true,
            description = "Path to the Warcraft 3 map file (.w3x or .w3m)."
    )
    File mapFile;

    @Option(
            names = {"-f", "--folder"},
            required = true,
            description = "Folder containing MDX/BLP asset files to import."
    )
    File assetsFolder;

    @Option(
            names = "--create-units",
            description = "Generate a custom unit object entry for each MDX model."
    )
    boolean createUnits;

    @Option(
            names = "--place-units",
            description = "Place unit instances on the map terrain (requires --create-units)."
    )
    boolean placeUnits;

    @Option(
            names = "--clear-units",
            description = "Remove all existing custom units before importing."
    )
    boolean clearUnits;

    @Option(
            names = "--clear-assets",
            description = "Remove all existing imported assets before importing new ones."
    )
    boolean clearAssets;

    @Option(
            names = {"-u", "--unit-def"},
            defaultValue = "hfoo",
            description = "Base unit ID to derive new unit definitions from (default: hfoo = Footman)."
    )
    String unitDefinition;

    /**
     * Called from {@link Main} when args are present.
     */
    public static int run(String[] args) {
        LOG.fine("CLI invoked with " + args.length + " argument(s)");
        return new CommandLine(new ImportCommand()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        LOG.info("CLI import: map=" + mapFile + " folder=" + assetsFolder
                + " createUnits=" + createUnits + " placeUnits=" + placeUnits);
        // Validate inputs
        if (!mapFile.exists()) {
            System.err.println("Error: map file not found: " + mapFile.getAbsolutePath());
            return 1;
        }
        if (!assetsFolder.exists() || !assetsFolder.isDirectory()) {
            System.err.println("Error: assets folder not found or not a directory: " + assetsFolder.getAbsolutePath());
            return 1;
        }

        // Discover assets
        System.out.println("Scanning assets in: " + assetsFolder.getAbsolutePath());
        AssetDiscoveryResult discovered = new AssetDiscoveryService().discover(assetsFolder);
        System.out.printf("Found %d MDX file(s), %d texture file(s)%n",
                discovered.mdxFiles().size(), discovered.textureFiles().size());

        if (discovered.totalFileCount() == 0) {
            System.err.println("Warning: no MDX or texture files found in the specified folder.");
            return 0;
        }

        // Collect all discovered files as absolute Paths
        Set<Path> allFiles = new LinkedHashSet<>();
        discovered.mdxFiles().forEach(rel -> allFiles.add(assetsFolder.toPath().resolve(rel).normalize()));
        discovered.textureFiles().forEach(rel -> allFiles.add(assetsFolder.toPath().resolve(rel).normalize()));

        // Build options
        ImportOptions opts = new ImportOptions(createUnits, placeUnits, clearUnits, clearAssets, unitDefinition);

        // Derive default output path: <name>_processed.<ext>
        String name = mapFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String ext = dot >= 0 ? name.substring(dot) : "";
        File outputFile = new File(mapFile.getParentFile(), base + "_processed" + ext);

        // Run import
        System.out.println("Processing map: " + mapFile.getName());
        System.out.println("Output file:    " + outputFile.getAbsolutePath());
        ImportResult result = new ImportService().process(
                mapFile, outputFile, allFiles, assetsFolder, opts,
                System.out::println, null);

        LOG.info("CLI import result: " + (result.success() ? "SUCCESS" : "FAILURE")
                + " output=" + outputFile.getAbsolutePath());
        return result.success() ? 0 : 1;
    }
}
