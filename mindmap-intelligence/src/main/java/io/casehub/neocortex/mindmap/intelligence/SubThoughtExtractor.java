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

    @Inject
    public SubThoughtExtractor(CaseMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    public void onExtractionRequested(@ObservesAsync SubThoughtExtractionRequested event) {
        try {
            LOG.fine(() -> "Sub-thought extraction requested for memory " + event.memoryId());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Sub-thought extraction failed for memory " + event.memoryId(), e);
        }
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
        }
        memoryStore.enrichAttributes(memoryId, attrs, tenantId);
    }

    public record ParsedSubThought(String type, String text, String entity) {}
}
