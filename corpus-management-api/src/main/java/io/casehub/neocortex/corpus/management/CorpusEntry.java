package io.casehub.neocortex.corpus.management;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CorpusEntry(
    String id,
    Map<String, Object> frontmatter,
    String body,
    String artifactRef
) {
    public String stringField(String key) {
        return (String) frontmatter.get(key);
    }

    @SuppressWarnings("unchecked")
    public List<String> stringListField(String key) {
        return (List<String>) frontmatter.get(key);
    }

    public Instant instantField(String key) {
        return (Instant) frontmatter.get(key);
    }

    public Integer intField(String key) {
        return (Integer) frontmatter.get(key);
    }

    public Double doubleField(String key) {
        return (Double) frontmatter.get(key);
    }
}
