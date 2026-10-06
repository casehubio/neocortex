package io.casehub.neocortex.knowledge;

import java.util.Objects;
import java.util.Set;

public record ExpandedTerm(String canonical, Set<String> variants) {
    public ExpandedTerm {
        Objects.requireNonNull(canonical);
        variants = Set.copyOf(variants);
    }

    public static ExpandedTerm passthrough(String term) {
        return new ExpandedTerm(term, Set.of(term));
    }
}
