package org.example.core.util;

import java.util.Set;

/**
 * Generates unique 4-character unit IDs for Warcraft 3 custom object modifications.
 * IDs follow the format [a-z][0-9]{3} (e.g. x000, x001 … z999, a000 …).
 * The generator starts at 'x' and cycles through all 26 letters × 1000 digits.
 */
public class UnitIDGenerator {

    /**
     * Returns the next available ID not already in {@code existingIds}.
     *
     * @param existingIds set of already-used unit IDs
     * @return next unique ID string, or {@code null} if all 26,000 IDs are exhausted
     */
    public static String generateNextId(Set<String> existingIds) {
        // Start index at 'x'
        int startIndex = 'x' - 'a';

        // Try from 'x' to 'z', then loop from 'a' to 'w'
        for (int i = 0; i < 26; i++) {
            char prefix = (char) ('a' + (startIndex + i) % 26);

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
