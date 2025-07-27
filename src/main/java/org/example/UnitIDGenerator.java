package org.example;

import java.util.Set;

public class UnitIDGenerator {
    /**
     * Generates a unique 4-character ID (a000–z999), starting at x000.
     * After z999, it loops back to a000 and goes up to w999.
     *
     * @param existingIds set of used IDs
     * @return next available unique ID, or null if all IDs are taken
     */
    public static String generateNextId(Set<String> existingIds) {
        // Total range is 26 letters (a–z), but we start at x
        char[] letters = new char[26];
        for (int i = 0; i < 26; i++) {
            letters[i] = (char) ('a' + i);
        }

        // Start index at 'x'
        int startIndex = 'x' - 'a';

        // Try from 'x' to 'z', then loop from 'a' to 'w'
        for (int i = 0; i < 26; i++) {
            char prefix = letters[(startIndex + i) % 26];

            for (int j = 0; j <= 999; j++) {
                String id = String.format("%c%03d", prefix, j);
                if (!existingIds.contains(id)) {
                    return id;
                }
            }
        }

        // All possible IDs are used
        return null;
    }
}
