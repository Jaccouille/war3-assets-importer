package org.example.cli;

import org.example.core.model.AssetDiscoveryResult;
import org.example.core.model.ImportOptions;
import org.example.core.model.ImportResult;
import org.example.core.service.AssetDiscoveryService;
import org.example.core.service.ImportService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

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

    @Override
    public Integer call() throws Exception {
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
        System.out.printf("Found %d MDX file(s), %d BLP file(s)%n",
                discovered.mdxFiles().size(), discovered.blpFiles().size());

        if (discovered.totalFileCount() == 0) {
            System.err.println("Warning: no MDX or BLP files found in the specified folder.");
            return 0;
        }

        // Collect all discovered files as absolute Paths
        Set<Path> allFiles = new LinkedHashSet<>();
        discovered.mdxFiles().forEach(rel -> allFiles.add(assetsFolder.toPath().resolve(rel).normalize()));
        discovered.blpFiles().forEach(rel -> allFiles.add(assetsFolder.toPath().resolve(rel).normalize()));

        // Build options
        ImportOptions opts = new ImportOptions(createUnits, placeUnits, clearUnits, clearAssets, unitDefinition);

        // Run import
        System.out.println("Processing map: " + mapFile.getName());
        ImportResult result = new ImportService().process(
                mapFile, allFiles, assetsFolder, opts, System.out::println);

        return result.success() ? 0 : 1;
    }

    /** Called from {@link org.example.Main} when args are present. */
    public static int run(String[] args) {
        return new CommandLine(new ImportCommand()).execute(args);
    }
}
