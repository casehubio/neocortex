package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import io.casehub.neocortex.memory.experience.SubThoughtTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class LifeEventHandler implements BiographyHandler {

    private final CaseMemoryStore memoryStore;

    @Inject
    public LifeEventHandler(CaseMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.LIFE_EVENT); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        for (var entry : profile.lifeEvents()) {
            var attrs = new HashMap<String, String>();
            attrs.put("event-type", "observation");
            attrs.put("provenance", "biographical-import");
            attrs.put("template-ref", "life-events.yaml#" + entry.id());
            if (entry.sourceRef() != null) attrs.put("source-ref", entry.sourceRef());
            if (entry.timestamp() != null) attrs.put("timestamp", entry.timestamp());

            if (entry.subThoughts() != null && !entry.subThoughts().isEmpty()) {
                attrs.put(SubThoughtAttributeKeys.COUNT, String.valueOf(entry.subThoughts().size()));
                for (int i = 0; i < entry.subThoughts().size(); i++) {
                    var st = entry.subThoughts().get(i);
                    SubThoughtTypes.validate(st.type());
                    attrs.put(SubThoughtAttributeKeys.type(i), st.type());
                    attrs.put(SubThoughtAttributeKeys.text(i), st.text());
                }
            }

            var input = MemoryInput.of(
                Subject.of("agent", agentId), ExperienceEvents.DOMAIN, tenantId,
                entry.description())
                .withAttributes(attrs);

            if (entry.pad() != null) {
                input = input.withPad(entry.pad().pleasure(), entry.pad().arousal(), entry.pad().dominance());
            }

            memoryStore.store(input);
        }
    }
}
