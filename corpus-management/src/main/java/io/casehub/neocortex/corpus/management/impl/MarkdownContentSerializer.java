package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.ContentSerializer;
import io.casehub.neocortex.corpus.management.CorpusEntry;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MarkdownContentSerializer implements ContentSerializer {

    @Override
    public byte[] serialize(CorpusEntry entry) {
        var sb = new StringBuilder();
        sb.append("---\n");
        new TreeMap<>(entry.metadata()).forEach((key, value) -> {
            if (value instanceof String s) {
                sb.append(key).append(": ").append(s).append("\n");
            } else if (value instanceof List<?> list) {
                sb.append(key).append(":\n");
                for (Object item : list) {
                    sb.append("  - ").append(item).append("\n");
                }
            }
        });
        sb.append("---\n\n");
        if (entry.body() != null) {
            sb.append(entry.body()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
