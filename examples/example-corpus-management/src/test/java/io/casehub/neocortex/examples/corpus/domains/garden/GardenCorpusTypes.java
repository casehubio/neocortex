package io.casehub.neocortex.examples.corpus.domains.garden;

import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FieldSchema;

import java.util.Map;

public final class GardenCorpusTypes {

    public static final CorpusType GARDEN = new CorpusType(
        "knowledge-garden-example",
        Map.of(
            "id", FieldSchema.STRING,
            "title", FieldSchema.STRING,
            "type", FieldSchema.STRING,
            "domain", FieldSchema.STRING
        ),
        Map.of(
            "tags", FieldSchema.STRING_LIST,
            "score", FieldSchema.DOUBLE,
            "staleness_threshold", FieldSchema.DURATION,
            "invalidation_triggers", FieldSchema.STRING_LIST
        ),
        null
    );

    private GardenCorpusTypes() {}
}
