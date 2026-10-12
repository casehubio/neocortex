package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.memory.reflection.ReflectionEvents;
import io.casehub.neocortex.memory.relationship.RelationshipEvents;
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
        var entry = entryWithDomain(null, "Contracted polio at age six.",
            new PadValues(-0.6, 0.5, -0.7), subThoughts);

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

    @Test
    void nullDomainDefaultsToExperience() {
        var store = new InMemoryMemoryStore(principal);
        var handler = new LifeEventHandler(store);

        var entry = entryWithDomain(null, "A childhood observation.");
        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        assertStoredUnderDomain(store, ExperienceEvents.DOMAIN, "A childhood observation.");
    }

    @Test
    void relationshipDomainRoutesToRelationshipStore() {
        var store = new InMemoryMemoryStore(principal);
        var handler = new LifeEventHandler(store);

        var entry = entryWithDomain("relationship", "Met Diego Rivera at an exhibition.");
        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        assertStoredUnderDomain(store, RelationshipEvents.DOMAIN, "Met Diego Rivera at an exhibition.");
        assertNothingUnderDomain(store, ExperienceEvents.DOMAIN);
    }

    @Test
    void reflectionDomainRoutesToReflectionStore() {
        var store = new InMemoryMemoryStore(principal);
        var handler = new LifeEventHandler(store);

        var entry = entryWithDomain("reflection", "I paint my own reality.");
        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        assertStoredUnderDomain(store, ReflectionEvents.DOMAIN, "I paint my own reality.");
        assertNothingUnderDomain(store, ExperienceEvents.DOMAIN);
    }

    @Test
    void unknownDomainDefaultsToExperience() {
        var store = new InMemoryMemoryStore(principal);
        var handler = new LifeEventHandler(store);

        var entry = entryWithDomain("unknown-domain", "Something unexpected.");
        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        assertStoredUnderDomain(store, ExperienceEvents.DOMAIN, "Something unexpected.");
    }

    private void assertStoredUnderDomain(InMemoryMemoryStore store, MemoryDomain domain, String expectedText) {
        var results = store.query(
            MemoryQuery.forSubject(Subject.of("agent", "agent-1"), domain, TENANT));
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().text()).isEqualTo(expectedText);
    }

    private void assertNothingUnderDomain(InMemoryMemoryStore store, MemoryDomain domain) {
        var results = store.query(
            MemoryQuery.forSubject(Subject.of("agent", "agent-1"), domain, TENANT));
        assertThat(results).isEmpty();
    }

    private static LifeEventEntry entryWithDomain(String domain, String description) {
        return entryWithDomain(domain, description, null, List.of());
    }

    private static LifeEventEntry entryWithDomain(String domain, String description,
            PadValues pad, List<LifeEventEntry.SubThought> subThoughts) {
        return new LifeEventEntry("test-event", "source.md#test", null,
            "1913-01-01", description,
            domain, null, null, null, null,
            pad,
            null, null, null, null, null, null, null, null, null,
            subThoughts, List.of());
    }

    private BiographyProfile profileWith(List<LifeEventEntry> events) {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), events, List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of());
    }
}
