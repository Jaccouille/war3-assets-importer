package org.example.gui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for converting filenames between different naming conventions.
 */
public class NameFormatter {

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])");
    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("[_-]");

    /**
     * Formats a filename according to the specified format.
     *
     * @param filename the input filename (e.g. "my_model_name.mdx")
     * @param format   the desired format: "Space Separated", "Space Separated (keep case)", "camelCase", "snake_case", or "UPPER_CASE"
     * @return the formatted name (without file extension)
     */
    public static String format(String filename, String format) {
        // Remove file extension
        String baseName = removeExtension(filename);

        // Convert to a standard internal representation (space-separated words)
        String[] words = toWords(baseName);

        return switch (format) {
            case "Space Separated" -> spaceSeparated(words);
            case "Space Separated (keep case)" -> spaceSeparatedKeepCase(words);
            case "camelCase" -> camelCase(words);
            case "snake_case" -> snakeCase(words);
            case "UPPER_CASE" -> upperCase(words);
            default -> baseName;
        };
    }

    private static String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

    /**
     * Splits the input into words by detecting:
     * - camelCase transitions (e.g. "MyModel" → ["My", "Model"])
     * - snake_case/kebab-case (e.g. "my_model" → ["my", "model"])
     * - spaces (e.g. "My Model" → ["My", "Model"])
     */
    private static String[] toWords(String input) {
        // Replace underscores and hyphens with spaces
        input = input.replaceAll("[_-]", " ");

        // Insert space before uppercase letters in camelCase
        input = CAMEL_CASE_PATTERN.matcher(input).replaceAll("$1 $2");

        // Split on whitespace and filter empty strings
        return input.trim().split("\\s+");
    }

    private static String spaceSeparated(String[] words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(capitalize(words[i]));
        }
        return sb.toString();
    }

    private static String spaceSeparatedKeepCase(String[] words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
    }

    private static String camelCase(String[] words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();
            if (i == 0) {
                sb.append(word);
            } else {
                sb.append(capitalize(word));
            }
        }
        return sb.toString();
    }

    private static String snakeCase(String[] words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append("_");
            sb.append(words[i].toLowerCase());
        }
        return sb.toString();
    }

    private static String upperCase(String[] words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append("_");
            sb.append(words[i].toUpperCase());
        }
        return sb.toString();
    }

    private static String capitalize(String word) {
        if (word.isEmpty()) return word;
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
    }
}
