package io.casehub.neocortex.cognition.memory;

import java.util.List;

public class NoOpConsolidationSummariser implements ConsolidationSummariser {
    @Override
    public String summarise(List<String> texts) {
        return "";
    }
}
