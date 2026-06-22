package io.casehub.rag.hyde;

import io.casehub.rag.QueryExpander;
import io.casehub.rag.RetrievalQuery;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProperty(name = "casehub.rag.hyde.mode", stringValue = "template")
public class TemplateQueryExpander implements QueryExpander {

    static final String DEFAULT_TEMPLATE =
        "A document that answers the question \"%s\" would contain the following information:";

    private final HydeConfig config;

    @Inject
    public TemplateQueryExpander(HydeConfig config) {
        this.config = config;
    }

    @Override
    public RetrievalQuery expand(RetrievalQuery query) {
        String expanded = config.template().orElse(DEFAULT_TEMPLATE)
            .formatted(query.text());
        return query.withExpansion(expanded);
    }
}
