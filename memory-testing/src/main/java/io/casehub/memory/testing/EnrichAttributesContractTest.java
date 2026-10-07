package io.casehub.memory.testing;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Map;

public abstract class EnrichAttributesContractTest {

    protected abstract CaseMemoryStore store();
    protected abstract String tenantId();

    @Test
    void enrichAttributesMergesIntoExistingMemory() {
        String memoryId = store().store(
            MemoryInput.of(Subject.of("agent", "a1"),
                ExperienceEvents.DOMAIN, tenantId(), "Lunch with Sarah"));

        store().enrichAttributes(memoryId,
            Map.of(SubThoughtAttributeKeys.COUNT, "1",
                   SubThoughtAttributeKeys.type(0), "affect-observation",
                   SubThoughtAttributeKeys.text(0), "She seemed distracted"),
            tenantId());

        var results = store().query(MemoryQuery.forSubject(
            Subject.of("agent", "a1"), ExperienceEvents.DOMAIN, tenantId()));
        assertThat(results).hasSize(1);
        var memory = results.getFirst();
        assertThat(memory.attributes()).containsEntry(
            SubThoughtAttributeKeys.COUNT, "1");
        assertThat(memory.attributes()).containsEntry(
            SubThoughtAttributeKeys.type(0), "affect-observation");
        assertThat(memory.attributes()).containsEntry(
            SubThoughtAttributeKeys.text(0), "She seemed distracted");
    }

    @Test
    void enrichAttributesPreservesExistingAttributes() {
        String memoryId = store().store(
            MemoryInput.of(Subject.of("agent", "a1"),
                ExperienceEvents.DOMAIN, tenantId(), "Test memory")
                .withAttribute("event-type", "observation"));

        store().enrichAttributes(memoryId,
            Map.of(SubThoughtAttributeKeys.COUNT, "1"),
            tenantId());

        var results = store().query(MemoryQuery.forSubject(
            Subject.of("agent", "a1"), ExperienceEvents.DOMAIN, tenantId()));
        var memory = results.getFirst();
        assertThat(memory.attributes()).containsEntry("event-type", "observation");
        assertThat(memory.attributes()).containsEntry(
            SubThoughtAttributeKeys.COUNT, "1");
    }

    @Test
    void enrichAttributesThrowsForNonexistentMemory() {
        assertThatThrownBy(() ->
            store().enrichAttributes("nonexistent-id",
                Map.of("key", "value"), tenantId()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enrichAttributesCapabilityDeclared() {
        assertThat(store().capabilities())
            .contains(MemoryCapability.ENRICH_ATTRIBUTES);
    }
}
