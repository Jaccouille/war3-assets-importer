package com.hiveworkshop.war3assetsimporter;

import com.hiveworkshop.blizzard.blp.BLPReaderSpi;
import com.hiveworkshop.blizzard.blp.BLPWriterSpi;
import com.hiveworkshop.war3assetsimporter.cli.ImportCommand;
import com.hiveworkshop.war3assetsimporter.gui.MainFrame;
import com.hiveworkshop.war3assetsimporter.gui.i18n.Messages;
import com.hiveworkshop.war3assetsimporter.gui.settings.AppearanceConfig;
import net.nikr.dds.DDSImageReaderSpi;

import javax.imageio.spi.IIORegistry;
import javax.swing.*;

/**
 * Application entry point.
 *
 * <ul>
 *   <li>No arguments launches the Swing GUI</li>
 *   <li>Any arguments delegates to the picocli CLI (--help for usage)</li>
 * </ul>
 * <p>
 * GUI:  java -jar war3importer.jar
 * CLI:  java -jar war3importer.jar -m map.w3x -f models/ --create-units --place-units
 */
public class Main {

    public static void main(String[] args) {
        // The blp-iio-plugin.jar ships without META-INF/services registration,
        // so ImageIO cannot auto-discover it via ServiceLoader.  Register the
        // SPI providers manually before any ImageIO call is made.
        registerBlpPlugin();

        if (args.length == 0) {
            // Register FlatLaf themes so they appear in UIManager.getInstalledLookAndFeels().
            // Uses the standard Swing API directly (FlatLaf 3.x removed installLookAndFeel()).
            UIManager.installLookAndFeel("FlatLaf Light", "com.formdev.flatlaf.FlatLightLaf");
            UIManager.installLookAndFeel("FlatLaf Dark", "com.formdev.flatlaf.FlatDarkLaf");
            UIManager.installLookAndFeel("FlatLaf IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf");
            UIManager.installLookAndFeel("FlatLaf Darcula", "com.formdev.flatlaf.FlatDarculaLaf");

            // Load and apply the saved L&F and locale before any Swing components are created
            AppearanceConfig appearanceConfig = new AppearanceConfig();
            appearanceConfig.load();
            if (appearanceConfig.getLocaleLanguage() != null) {
                Messages.setLocale(new java.util.Locale(appearanceConfig.getLocaleLanguage()));
            }
            appearanceConfig.applyIfSet();

            SwingUtilities.invokeLater(() -> new MainFrame(appearanceConfig));
        } else {
            System.exit(ImportCommand.run(args));
        }
    }

    private static void registerBlpPlugin() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(new BLPReaderSpi());
        registry.registerServiceProvider(new BLPWriterSpi());
        registry.registerServiceProvider(new DDSImageReaderSpi());
    }
}
