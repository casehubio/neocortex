package io.casehub.neocortex.rag;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface TextChunker {

    List<String> chunk(String text);

    static TextChunker recursive(int maxChunkSize, int overlap) {
        if (maxChunkSize <= 0) throw new IllegalArgumentException("maxChunkSize must be positive");
        if (overlap < 0) throw new IllegalArgumentException("overlap must be non-negative");
        if (overlap >= maxChunkSize) throw new IllegalArgumentException("overlap must be less than maxChunkSize");

        String[] separators = {"\n\n", "\n", ". ", " ", ""};
        return text -> splitRecursive(text, separators, 0, maxChunkSize, overlap);
    }

    private static List<String> splitRecursive(String text, String[] separators, int separatorIndex,
                                               int maxSize, int overlap) {
        if (text.length() <= maxSize) {
            return text.isBlank() ? List.of() : List.of(text);
        }
        if (separatorIndex >= separators.length) {
            return List.of(text.substring(0, maxSize));
        }

        String separator = separators[separatorIndex];
        String[] parts = separator.isEmpty()
                ? splitByCharacter(text, maxSize)
                : text.split(java.util.regex.Pattern.quote(separator), -1);

        if (parts.length <= 1) {
            return splitRecursive(text, separators, separatorIndex + 1, maxSize, overlap);
        }

        var chunks = new ArrayList<String>();
        var current = new StringBuilder();

        for (String part : parts) {
            String candidate = current.isEmpty()
                    ? part
                    : current + separator + part;

            if (candidate.length() > maxSize) {
                if (!current.isEmpty()) {
                    String chunk = current.toString().strip();
                    if (!chunk.isEmpty()) chunks.add(chunk);

                    if (overlap > 0 && chunk.length() > overlap) {
                        current = new StringBuilder(chunk.substring(chunk.length() - overlap));
                        candidate = current + separator + part;
                    } else {
                        current = new StringBuilder();
                        candidate = part;
                    }
                }

                if (candidate.length() > maxSize) {
                    var subChunks = splitRecursive(candidate, separators, separatorIndex + 1, maxSize, overlap);
                    if (!subChunks.isEmpty()) {
                        chunks.addAll(subChunks.subList(0, subChunks.size() - 1));
                        current = new StringBuilder(subChunks.getLast());
                    }
                } else {
                    current = new StringBuilder(candidate);
                }
            } else {
                current = new StringBuilder(candidate);
            }
        }

        if (!current.isEmpty()) {
            String chunk = current.toString().strip();
            if (!chunk.isEmpty()) chunks.add(chunk);
        }

        return List.copyOf(chunks);
    }

    private static String[] splitByCharacter(String text, int maxSize) {
        int count = (text.length() + maxSize - 1) / maxSize;
        String[] parts = new String[count];
        for (int i = 0; i < count; i++) {
            int start = i * maxSize;
            int end = Math.min(start + maxSize, text.length());
            parts[i] = text.substring(start, end);
        }
        return parts;
    }
}
