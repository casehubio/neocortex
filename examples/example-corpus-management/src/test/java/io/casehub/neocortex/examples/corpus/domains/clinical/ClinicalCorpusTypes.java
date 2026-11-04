package io.casehub.neocortex.examples.corpus.domains.clinical;

import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FieldSchema;

import java.util.Map;

public final class ClinicalCorpusTypes {

    public static final CorpusType CLINICAL_GUIDELINE = new CorpusType(
        "clinical-guideline",
        Map.of(
            "id", FieldSchema.STRING,
            "title", FieldSchema.STRING,
            "version", FieldSchema.STRING,
            "effective_date", FieldSchema.INSTANT,
            "condition", FieldSchema.STRING
        ),
        Map.of(
            "supersedes", FieldSchema.STRING,
            "evidence_grade", FieldSchema.STRING,
            "contraindications", FieldSchema.STRING_LIST,
            "review_date", FieldSchema.INSTANT
        ),
        null
    );

    private ClinicalCorpusTypes() {}
}
