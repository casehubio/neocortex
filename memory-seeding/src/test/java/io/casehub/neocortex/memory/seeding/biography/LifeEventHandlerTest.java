package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LifeEventHandlerTest {

    private static final String TENANT = "t1";

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "agent-1"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @Test
    void storesWithSubThoughtAttributesAtomically() {
        var store = new InMemoryMemoryStore(principal);
        var handler = new LifeEventHandler(store);

        var subThoughts = List.of(
            new LifeEventEntry.SubThought("affect-observation", "The isolation was profound."),
            new LifeEventEntry.SubThought("self-reflection", "First fracture between body and will.")
        );
        var entry = new LifeEventEntry("polio", "source.md#polio", null,
            "1913-01-01", "Contracted polio at age six.",
            new LifeEventEntry.PadValues(-0.6, 0.5, -0.7),
            subThoughts, List.of());

        var profile = profileWith(List.of(entry));
        handler.handle(profile, "agent-1", TENANT);

        var results = store.query(
            MemoryQuery.forSubject(Subject.of("agent", "agent-1"), ExperienceEvents.DOMAIN, TENANT));
        assertThat(results).hasSize(1);
        var memory = results.getFirst();
        assertThat(memory.text()).isEqualTo("Contracted polio at age six.");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.COUNT, "2");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.type(0), "affect-observation");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.text(0), "The isolation was profound.");
        assertThat(memory.attributes()).containsEntry("provenance", "biographical-import");
        assertThat(memory.pleasure()).isEqualTo(-0.6);
    }

    private BiographyProfile profileWith(List<LifeEventEntry> events) {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), events, List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
