package io.casehub.corpus.zip;

import io.casehub.corpus.ChangeSet;
import io.casehub.corpus.ChangeSource;
import io.casehub.corpus.ChangeType;
import io.casehub.corpus.ChangedEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filesystem-based implementation of {@link ChangeSource}.
 *
 * <p>Cursor is a JSON-serialized snapshot of known paths and their modification times.
 * Null cursor is treated as an empty map (behaves like {@link #fullScan()}).
 */
public final class FlatChangeSource implements ChangeSource {

    private final FlatCorpusStore store;
    private final Path rootDir;

    public FlatChangeSource(FlatCorpusStore store, Path rootDir) {
        this.store = store;
        this.rootDir = rootDir;
    }

    @Override
    public ChangeSet fullScan() {
        List<ChangedEntry> entries = new ArrayList<>();
        Map<String, Long> currentState = new HashMap<>();

        for (String path : store.list()) {
            entries.add(new ChangedEntry(path, ChangeType.ADDED));
            currentState.put(path, getLastModified(path));
        }

        return new ChangeSet(entries, serializeCursor(currentState));
    }

    @Override
    public ChangeSet changesSince(String cursor) {
        Map<String, Long> previousState = deserializeCursor(cursor);
        Map<String, Long> currentState = new HashMap<>();
        List<ChangedEntry> entries = new ArrayList<>();

        // Scan current filesystem state
        for (String path : store.list()) {
            long currentMtime = getLastModified(path);
            currentState.put(path, currentMtime);

            Long previousMtime = previousState.get(path);
            if (previousMtime == null) {
                entries.add(new ChangedEntry(path, ChangeType.ADDED));
            } else if (currentMtime != previousMtime) {
                entries.add(new ChangedEntry(path, ChangeType.MODIFIED));
            }
        }

        // Detect deletions (in previous but not current)
        for (String path : previousState.keySet()) {
            if (!currentState.containsKey(path)) {
                entries.add(new ChangedEntry(path, ChangeType.DELETED));
            }
        }

        return new ChangeSet(entries, serializeCursor(currentState));
    }

    private long getLastModified(String path) {
        try {
            return Files.getLastModifiedTime(rootDir.resolve(path)).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get mtime for: " + path, e);
        }
    }

    private Map<String, Long> deserializeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new HashMap<>();
        }

        Map<String, Long> state = new HashMap<>();
        String trimmed = cursor.trim();

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Invalid cursor format");
        }

        String content = trimmed.substring(1, trimmed.length() - 1).trim();
        if (content.isEmpty()) {
            return state;
        }

        // Split by comma, handling quotes
        String[] entries = content.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor entry: " + entry);
            }

            String key = parts[0].trim();
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = unescapeJson(key.substring(1, key.length() - 1));
            }

            long value = Long.parseLong(parts[1].trim());
            state.put(key, value);
        }

        return state;
    }

    private String serializeCursor(Map<String, Long> state) {
        if (state.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        List<String> sortedPaths = new ArrayList<>(state.keySet());
        sortedPaths.sort(String::compareTo);

        for (String path : sortedPaths) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(path)).append("\":");
            sb.append(state.get(path));
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
