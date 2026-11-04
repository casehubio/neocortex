package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FieldSchema;

import java.util.Map;

public final class BuiltInCorpusTypes {

    public static final CorpusType KNOWLEDGE_GARDEN = new CorpusType(
        "knowledge-garden",
        Map.of("id", FieldSchema.STRING, "title", FieldSchema.STRING,
               "type", FieldSchema.STRING, "domain", FieldSchema.STRING),
        Map.of("tags", FieldSchema.STRING_LIST, "score", FieldSchema.DOUBLE,
               "staleness_threshold", FieldSchema.DURATION,
               "invalidation_triggers", FieldSchema.STRING_LIST),
        null
    );

    public static final CorpusType RESEARCH = new CorpusType(
        "research",
        Map.of("id", FieldSchema.STRING, "title", FieldSchema.STRING,
               "type", FieldSchema.STRING, "corpus", FieldSchema.STRING,
               "domain", FieldSchema.STRING),
        Map.of("tags", FieldSchema.STRING_LIST, "source_artifact", FieldSchema.STRING,
               "provenance", FieldSchema.MAP, "citations", FieldSchema.STRING_LIST,
               "staleness_threshold", FieldSchema.DURATION),
        null
    );

    public static final CorpusType TRANSCRIPT = new CorpusType(
        "transcript",
        Map.of("id", FieldSchema.STRING, "title", FieldSchema.STRING,
               "type", FieldSchema.STRING, "corpus", FieldSchema.STRING,
               "interaction_type", FieldSchema.STRING, "activity_type", FieldSchema.STRING,
               "artifact_ref", FieldSchema.STRING),
        Map.of("domain", FieldSchema.STRING, "participants", FieldSchema.STRING_LIST,
               "turn_count", FieldSchema.INTEGER, "tags", FieldSchema.STRING_LIST,
               "related_nodes", FieldSchema.STRING_LIST),
        null
    );

    private BuiltInCorpusTypes() {}
}
