package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.mood.MoodEvents;
import io.casehub.neocortex.memory.mood.MoodState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CurrentStateHandler implements BiographyHandler {

    private final CaseMemoryStore memoryStore;

    @Inject
    public CurrentStateHandler(CaseMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.CURRENT_STATE); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        for (var entry : profile.currentStates()) {
            if (entry.mood() != null) {
                var moodState = new MoodState(
                    agentId, tenantId, Instant.now(),
                    entry.mood().pleasure(), entry.mood().arousal(), entry.mood().dominance(),
                    "biographical-import", null, Set.of(), Map.of());
                memoryStore.store(MoodEvents.toMemoryInput(moodState));
            }

            if (entry.recentContext() != null) {
                var attrs = new HashMap<String, String>();
                attrs.put("provenance", "biographical-import");
                attrs.put("template-ref", "current-state.yaml#" + entry.id());
                if (entry.sourceRef() != null) attrs.put("source-ref", entry.sourceRef());
                if (entry.timestamp() != null) attrs.put("timestamp", entry.timestamp());

                memoryStore.store(MemoryInput.of(
                    Subject.of("agent", agentId), MoodEvents.DOMAIN, tenantId,
                    entry.recentContext())
                    .withAttributes(attrs));
            }
        }
    }
}
