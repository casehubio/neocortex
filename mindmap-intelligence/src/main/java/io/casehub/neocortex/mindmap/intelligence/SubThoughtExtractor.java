package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import io.casehub.neocortex.memory.experience.SubThoughtClassifier;
import io.casehub.neocortex.memory.experience.SubThoughtTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SubThoughtExtractor {

    private static final Logger LOG = Logger.getLogger(SubThoughtExtractor.class.getName());

    private final CaseMemoryStore memoryStore;
    private final jakarta.enterprise.event.Event<SubThoughtsEnriched> enrichedEvent;

    @Inject
    public SubThoughtExtractor(CaseMemoryStore memoryStore,
                                jakarta.enterprise.event.Event<SubThoughtsEnriched> enrichedEvent) {
        this.memoryStore = memoryStore;
        this.enrichedEvent = enrichedEvent;
    }

    public void onExtractionRequested(@ObservesAsync SubThoughtExtractionRequested event) {
        try {
            LOG.fine(() -> "Sub-thought extraction requested for memory " + event.memoryId());
            var subThoughts = extractFromText(event.experienceText());
            if (!subThoughts.isEmpty()) {
                applySubThoughts(event.memoryId(), subThoughts, event.tenantId());
                var agentId = event.principalId() != null ? event.principalId().id() : "unknown";
                enrichedEvent.fire(new SubThoughtsEnriched(
                        event.memoryId(), agentId, event.tenantId(), subThoughts));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Sub-thought extraction failed for memory " + event.memoryId(), e);
        }
    }

    List<ParsedSubThought> extractFromText(String text) {
        return SubThoughtClassifier.classify(text).stream()
                .map(m -> new ParsedSubThought(m.type(), m.text(), null, 0.8))
                .toList();
    }

    public void applySubThoughts(String memoryId, List<ParsedSubThought> subThoughts,
                                  String tenantId) {
        Map<String, String> attrs = new HashMap<>();
        attrs.put(SubThoughtAttributeKeys.COUNT, String.valueOf(subThoughts.size()));
        for (int i = 0; i < subThoughts.size(); i++) {
            var st = subThoughts.get(i);
            SubThoughtTypes.validate(st.type());
            attrs.put(SubThoughtAttributeKeys.type(i), st.type());
            attrs.put(SubThoughtAttributeKeys.text(i), st.text());
            if (st.entity() != null) {
                attrs.put(SubThoughtAttributeKeys.entity(i), st.entity());
            }
            attrs.put(SubThoughtAttributeKeys.confidence(i), String.valueOf(st.confidence()));
        }
        memoryStore.enrichAttributes(memoryId, attrs, tenantId);
    }

    public record ParsedSubThought(String type, String text, String entity, double confidence) {
        public ParsedSubThought(String type, String text, String entity) {
            this(type, text, entity, 0.8);
        }
    }
}
