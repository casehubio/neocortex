package io.casehub.neocortex.knowledge;

import java.util.Optional;

public interface QueryClassifier {
    Optional<KnowledgeQuery> classify(String naturalLanguage, String domain);
}
