package org.example.core.model;

/**
 * A unit definition entry read from a Warcraft 3 map's {@code war3map.w3u} file.
 *
 * @param id   4-character unit object ID (e.g. {@code "hfoo"}, {@code "u000"})
 * @param name display name from the {@code unam} field; may be empty for unnamed units
 */
public record UnitEntry(String id, String name) {

    /**
     * Returns {@code true} when this entry matches the given search query.
     * Matches case-insensitively against both the ID and the display name.
     * A blank or {@code null} query matches everything.
     */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase();
        return id.toLowerCase().contains(q) || name.toLowerCase().contains(q);
    }

    /** {@code "Name (id)"}, or just {@code "id"} when the name is blank. */
    @Override
    public String toString() {
        return name.isBlank() ? id : name + " (" + id + ")";
    }
}
