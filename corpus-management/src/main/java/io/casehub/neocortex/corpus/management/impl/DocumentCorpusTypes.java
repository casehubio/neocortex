package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FieldSchema;

import java.util.Map;

public final class DocumentCorpusTypes {

    public static final CorpusType DOCUMENT = new CorpusType(
        "document",
        Map.of("content_format", FieldSchema.STRING,
               "source_format", FieldSchema.STRING,
               "layout", FieldSchema.STRING),
        Map.of("artifact_ref", FieldSchema.STRING,
               "source_pages", FieldSchema.INTEGER),
        null
    );

    private DocumentCorpusTypes() {}
}
