package io.casehub.neocortex.corpus.management;

import java.util.Map;

public record CorpusType(
    String name,
    Map<String, FieldSchema> requiredFields,
    Map<String, FieldSchema> optionalFields,
    String coverTemplate
) {

}
