package io.casehub.neocortex.cognition.memory;

import java.util.List;

@FunctionalInterface
public interface ConsolidationSummariser {
    String summarise(List<String> texts);
}
