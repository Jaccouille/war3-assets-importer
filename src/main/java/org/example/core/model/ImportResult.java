package org.example.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImportResult {
    private final boolean success;
    private final List<String> logs;

    private ImportResult(boolean success, List<String> logs) {
        this.success = success;
        this.logs = Collections.unmodifiableList(new ArrayList<>(logs));
    }

    public static ImportResult success(List<String> logs) {
        return new ImportResult(true, logs);
    }

    public static ImportResult failure(List<String> logs) {
        return new ImportResult(false, logs);
    }

    public boolean success() { return success; }
    public List<String> logs() { return logs; }
}