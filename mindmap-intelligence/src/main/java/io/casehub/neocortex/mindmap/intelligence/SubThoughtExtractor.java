package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
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
        if (text == null || text.isBlank()) return List.of();
        var result = new java.util.ArrayList<ParsedSubThought>();
        for (var sentence : text.split("(?<=[.!?])\\s+")) {
            var trimmed = sentence.strip();
            if (trimmed.isEmpty()) continue;
            classifySentence(trimmed).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private java.util.Optional<ParsedSubThought> classifySentence(String sentence) {
        var lower = sentence.toLowerCase();
        if (containsAny(lower, "felt", "seemed", "appeared", "looked", "upset", "happy", "sad", "anxious"))
            return java.util.Optional.of(new ParsedSubThought(SubThoughtTypes.AFFECT_OBSERVATION, sentence, null, 0.8));
        if (containsAny(lower, "because", "since", "caused", "due to", "therefore", "as a result"))
            return java.util.Optional.of(new ParsedSubThought(SubThoughtTypes.CAUSAL_INFERENCE, sentence, null, 0.8));
        if (containsAny(lower, "should", "plan to", "going to", "need to", "want to", "intend"))
            return java.util.Optional.of(new ParsedSubThought(SubThoughtTypes.INTENTION, sentence, null, 0.8));
        if (containsAny(lower, "worry", "concerned", "afraid", "fear", "anxious about", "dread"))
            return java.util.Optional.of(new ParsedSubThought(SubThoughtTypes.CONCERN, sentence, null, 0.8));
        if (containsAny(lower, "i feel", "i think", "i wonder", "i notice", "i realize"))
            return java.util.Optional.of(new ParsedSubThought(SubThoughtTypes.SELF_REFLECTION, sentence, null, 0.8));
        if (containsAny(lower, "good", "bad", "excellent", "terrible", "wonderful", "awful"))
            return java.util.Optional.of(new ParsedSubThought(SubThoughtTypes.EVALUATIVE, sentence, null, 0.8));
        if (containsAny(lower, "reminds me", "similar to", "like when", "brings to mind"))
            return java.util.Optional.of(new ParsedSubThought(SubThoughtTypes.ASSOCIATION, sentence, null, 0.8));
        return java.util.Optional.empty();
    }

    private boolean containsAny(String text, String... keywords) {
        for (var kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
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
