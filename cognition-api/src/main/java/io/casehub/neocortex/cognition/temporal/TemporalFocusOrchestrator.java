package io.casehub.neocortex.cognition.temporal;

import io.casehub.neocortex.cognitive.index.AttentionItem;

import java.util.List;
import java.util.Set;

public interface TemporalFocusOrchestrator {
    void tick(String agentId, String tenantId, Set<String> relevantSubjects);

    List<AttentionItem> lastFocus();
}
