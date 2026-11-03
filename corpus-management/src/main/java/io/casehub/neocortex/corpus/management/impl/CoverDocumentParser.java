package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FieldSchema;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

public class CoverDocumentParser {

    private final Yaml yaml = new Yaml();

    public CorpusEntry parse(String coverDocument, CorpusType type) {
        String trimmed = coverDocument.stripLeading();
        if (!trimmed.startsWith("---")) {
            throw new IllegalArgumentException("Cover document must start with YAML frontmatter delimiter '---'");
        }

        int secondDelimiter = trimmed.indexOf("---", 3);
        if (secondDelimiter < 0) {
            throw new IllegalArgumentException("Cover document must have closing YAML frontmatter delimiter '---'");
        }

        String yamlContent = trimmed.substring(3, secondDelimiter).strip();
        String body = trimmed.substring(secondDelimiter + 3).strip();

        Map<String, Object> frontmatter = yaml.load(yamlContent);
        if (frontmatter == null) {
            frontmatter = new LinkedHashMap<>();
        }

        for (Map.Entry<String, FieldSchema> required : type.requiredFields().entrySet()) {
            if (!frontmatter.containsKey(required.getKey())) {
                throw new IllegalArgumentException(
                    "Missing required field '" + required.getKey() + "' for corpus type '" + type.name() + "'");
            }
        }

        String id = (String) frontmatter.get("id");
        String artifactRef = frontmatter.containsKey("artifact_ref")
            ? (String) frontmatter.get("artifact_ref")
            : null;

        return new CorpusEntry(id, Map.copyOf(frontmatter), body, artifactRef);
    }
}
