package io.casehub.neocortex.corpus.management;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CorpusEntry(
    String id,
    Map<String, Object> metadata,
    String body,
    String artifactRef
) {
    public String stringField(String key) {
        return (String) metadata.get(key);
    }

    @SuppressWarnings("unchecked")
    public List<String> stringListField(String key) {
        return (List<String>) metadata.get(key);
    }

    public Instant instantField(String key) {
        return (Instant) metadata.get(key);
    }

    public Integer intField(String key) {
        return (Integer) metadata.get(key);
    }

    public Double doubleField(String key) {
        return (Double) metadata.get(key);
    }
}
