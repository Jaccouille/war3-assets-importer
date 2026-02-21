package org.example;

import org.example.cli.ImportCommand;
import org.example.gui.MainFrame;

import javax.swing.*;

/**
 * Application entry point.
 *
 * <ul>
 *   <li>No arguments launches the Swing GUI</li>
 *   <li>Any arguments delegates to the picocli CLI (--help for usage)</li>
 * </ul>
 *
 * GUI:  java -jar war3importer.jar
 * CLI:  java -jar war3importer.jar -m map.w3x -f models/ --create-units --place-units
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            SwingUtilities.invokeLater(MainFrame::new);
        } else {
            System.exit(ImportCommand.run(args));
        }
    }
}
