package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class EntryContentParser {

    private EntryContentParser() {}

    static CorpusEntry parse(String entryId, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!text.startsWith("---")) {
            return new CorpusEntry(entryId, Map.of(), text.strip(), null);
        }

        int closingIndex = text.indexOf("\n---", 3);
        if (closingIndex < 0) {
            return new CorpusEntry(entryId, Map.of(), text.strip(), null);
        }

        String yamlBlock = closingIndex > 4 ? text.substring(4, closingIndex).trim() : "";
        String body = text.substring(closingIndex + 4).strip();

        Map<String, Object> metadata;
        try {
            metadata = new Yaml().load(yamlBlock);
        } catch (Exception e) {
            return new CorpusEntry(entryId, Map.of(), text.strip(), null);
        }
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }

        String artifactRef = metadata.get("artifact_ref") instanceof String s ? s : null;
        return new CorpusEntry(entryId, Map.copyOf(metadata), body, artifactRef);
    }
}
