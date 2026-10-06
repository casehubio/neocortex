package io.casehub.neocortex.knowledge.normalization;

import io.casehub.neocortex.knowledge.KnowledgeQuery;
import io.casehub.neocortex.knowledge.QueryClassifier;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpQueryClassifier implements QueryClassifier {

    @Override
    public Optional<KnowledgeQuery> classify(String naturalLanguage, String domain) {
        return Optional.empty();
    }
}
