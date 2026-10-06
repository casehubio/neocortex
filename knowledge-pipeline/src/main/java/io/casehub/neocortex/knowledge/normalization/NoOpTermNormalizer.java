package io.casehub.neocortex.knowledge.normalization;

import io.casehub.neocortex.knowledge.ExpandedTerm;
import io.casehub.neocortex.knowledge.TermNormalizer;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpTermNormalizer implements TermNormalizer {

    @Override
    public ExpandedTerm normalize(String term, String domain) {
        return ExpandedTerm.passthrough(term);
    }
}
