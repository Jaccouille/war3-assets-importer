package org.example.gui;

import org.example.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;

/**
 * Modal help dialog that documents the full workflow and every configuration
 * option available in the application.
 */
public class HelpDialog extends JDialog {

    public HelpDialog(Frame owner) {
        super(owner, Messages.get("help.title"), true);
        setSize(720, 580);
        setLocationRelativeTo(owner);
        setResizable(true);

        JEditorPane editorPane = new JEditorPane("text/html", buildHtml());
        editorPane.setEditable(false);
        editorPane.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(editorPane);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeBtn);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        // Close on Escape
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(closeBtn);
    }

    private static String buildHtml() {
        return "<html><body style='font-family: sans-serif; margin: 14px; font-size: 13px;'>"

                // ----------------------------------------------------------------
                + "<h2 style='margin-top:0'>Warcraft 3 Model Importer</h2>"
                + "<p>Imports custom assets (3D models, textures, sounds, and any other files) into a "
                + "Warcraft III map file (.w3x / .w3m), and optionally creates unit definitions so "
                + "the models are usable inside the World Editor and in-game.</p>"

                // ----------------------------------------------------------------
                + "<h3>Quick Start</h3>"
                + "<ol>"
                + "<li>Click <b>Open Map</b> to load a <code>.w3x</code> or <code>.w3m</code> map.</li>"
                + "<li>Click <b>Import Models Folder</b> to scan a folder containing the assets you want to import.</li>"
                + "<li>Select the files you want to import using the checkboxes in the <b>Assets</b> tab.</li>"
                + "<li>Configure options in the <b>Import Configuration</b> tab.</li>"
                + "<li>Click <b>Process and Save</b> — the processed map is written to the Output path.</li>"
                + "</ol>"

                // ----------------------------------------------------------------
                + "<h3>Assets Tab</h3>"
                + "<p>Displays discovered assets in two selectable views:</p>"
                + "<ul>"
                + "<li><b>Category View</b> — files grouped by type (Models / Textures), "
                + "organised into the same folder hierarchy they have on disk.</li>"
                + "<li><b>Folder View</b> — the raw filesystem tree rooted at the chosen folder. "
                + "Shows file/folder count and total size in parentheses.</li>"
                + "</ul>"
                + "<p>Use checkboxes to select which files to import. "
                + "Checking a folder node selects everything inside it. "
                + "Clicking a file node previews the asset on the right.</p>"

                // ----------------------------------------------------------------
                + "<h3>Import Configuration Tab</h3>"

                + "<h4>Unit Creation</h4>"
                + "<table cellpadding='4' width='100%'>"
                + row("Create units",
                        "Add a custom unit definition for each selected MDX model. "
                        + "Each unit appears as a new entry in the Object Editor (W3U file).")
                + row("Place units on terrain",
                        "Place the created units on the map terrain. "
                        + "Draw a rectangle on the map preview to define the placement area, "
                        + "or leave it empty to use the full camera-bounded map area.")
                + row("Clear all units",
                        "Remove all existing unit placements from the map before importing. "
                        + "Useful for re-importing without accumulating duplicates.")
                + row("Clear existing assets",
                        "Delete all previously imported assets from the map archive "
                        + "before adding the new selection. Use this to replace an old import without leaving stale files.")
                + row("Keep filename only",
                        "Store imported assets without their subfolder path "
                        + "(e.g. <code>Infantry/soldier.mdx</code> → <code>soldier.mdx</code>). "
                        + "Command-button textures (BTN*/DISBTN*) always use their canonical WC3 paths "
                        + "regardless of this setting.")
                + row("Unit Scaling",
                        "Scale factor applied to the model size of created units. "
                        + "1.0 = original size, 2.0 = double size.")
                + "</table>"

                + "<h4>Unit Placement</h4>"
                + "<table cellpadding='4' width='100%'>"
                + row("Unit Origin ID",
                        "Base unit type ID used as the template for created units "
                        + "(e.g. <code>hfoo</code> for Human Footman). "
                        + "The new units inherit their base stats from this type. "
                        + "Type to search existing unit definitions in the loaded map.")
                + row("Unit Angle",
                        "Facing direction of placed units in degrees. "
                        + "0° = East &nbsp; 90° = North &nbsp; 180° = West &nbsp; 270° = South "
                        + "(default WC3 camera direction).")
                + row("Spacing X / Y",
                        "Horizontal and vertical gap between units on the placement grid, "
                        + "measured in pixels relative to the displayed map image. "
                        + "Smaller values pack units more tightly.")
                + row("Placing Order",
                        "Whether the placement grid fills left-to-right row by row, "
                        + "or top-to-bottom column by column.")
                + "</table>"

                + "<h4>Unit Naming</h4>"
                + "<table cellpadding='4' width='100%'>"
                + row("Auto-name from MDX filename",
                        "Automatically set each unit's display name by converting its MDX filename. "
                        + "Choose a style with the Format dropdown.")
                + row("Auto-assign BTN icon",
                        "If a BTN* texture matching the MDX filename is found among selected assets, "
                        + "automatically assign it as the unit's command-card icon.")
                + row("Format",
                        "How the filename is converted into a display name:<br>"
                        + "&nbsp;&nbsp;<b>Space Separated (keep case)</b>: <code>InfantryAmerican</code> → <i>Infantry American</i><br>"
                        + "&nbsp;&nbsp;<b>Space Separated</b>: → <i>Infantry american</i><br>"
                        + "&nbsp;&nbsp;<b>camelCase</b>: → <i>infantryAmerican</i><br>"
                        + "&nbsp;&nbsp;<b>snake_case</b>: → <i>infantry_american</i><br>"
                        + "&nbsp;&nbsp;<b>UPPER_CASE</b>: → <i>INFANTRY_AMERICAN</i>")
                + row("Preview",
                        "Opens a table showing how every selected MDX filename will be formatted "
                        + "with the current settings.")
                + "</table>"

                + "<h4>Map Preview</h4>"
                + "<p>The right-hand panel shows the map image and lets you define the unit placement area.</p>"
                + "<ul>"
                + "<li>Left-click and drag to draw a rectangle on the map image.</li>"
                + "<li>The blue triangles show where units will be placed at the current spacing.</li>"
                + "<li>Yellow triangles show units already present in the map.</li>"
                + "<li>Click <b>Clear</b> to remove the drawn shape; units will then use the full map area.</li>"
                + "<li>The status indicator is <span style='color:green'><b>green</b></span> when all units fit, "
                + "<span style='color:red'><b>red</b></span> when the area is too small "
                + "(increase the shape size or raise the spacing values).</li>"
                + "</ul>"

                + "</body></html>";
    }

    private static String row(String term, String description) {
        return "<tr valign='top'>"
                + "<td width='160' style='font-weight:bold;white-space:nowrap'>" + term + "</td>"
                + "<td>" + description + "</td>"
                + "</tr>";
    }
}
