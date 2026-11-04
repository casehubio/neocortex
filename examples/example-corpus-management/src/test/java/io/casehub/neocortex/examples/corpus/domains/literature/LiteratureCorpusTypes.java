package io.casehub.neocortex.examples.corpus.domains.literature;

import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FieldSchema;

import java.util.Map;

public final class LiteratureCorpusTypes {

    public static final CorpusType LITERATURE = new CorpusType(
        "academic-literature",
        Map.of(
            "id", FieldSchema.STRING,
            "title", FieldSchema.STRING,
            "authors", FieldSchema.STRING_LIST,
            "year", FieldSchema.INTEGER,
            "abstract", FieldSchema.STRING
        ),
        Map.of(
            "doi", FieldSchema.STRING,
            "journal", FieldSchema.STRING,
            "citations", FieldSchema.STRING_LIST,
            "keywords", FieldSchema.STRING_LIST,
            "methodology", FieldSchema.STRING
        ),
        null
    );

    private LiteratureCorpusTypes() {}
}
