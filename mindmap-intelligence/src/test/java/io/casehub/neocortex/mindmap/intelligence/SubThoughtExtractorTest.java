package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import io.casehub.neocortex.memory.experience.SubThoughtTypes;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubThoughtExtractorTest {

    private static final String TENANT = "t1";
    private static final Subject SUBJECT = Subject.of("agent", "a1");
    private InMemoryMemoryStore memoryStore;
    private SubThoughtExtractor extractor;

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "a1"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @BeforeEach
    void setUp() {
        memoryStore = new InMemoryMemoryStore(principal);
        extractor = new SubThoughtExtractor(memoryStore, new NoOpEvent<>());
    }

    @Test
    void enrichesMemoryWithParsedSubThoughts() {
        String memoryId = memoryStore.store(MemoryInput.of(
            SUBJECT, ExperienceEvents.DOMAIN, TENANT, "Lunch with Sarah"));

        var parsed = List.of(
            new SubThoughtExtractor.ParsedSubThought(
                SubThoughtTypes.AFFECT_OBSERVATION, "She seemed distracted", "Sarah"),
            new SubThoughtExtractor.ParsedSubThought(
                SubThoughtTypes.INTENTION, "Should bring David here", "La Trattoria")
        );

        extractor.applySubThoughts(memoryId, parsed, TENANT);

        var results = memoryStore.query(
            MemoryQuery.forSubject(SUBJECT, ExperienceEvents.DOMAIN, TENANT));
        assertThat(results).hasSize(1);
        var memory = results.getFirst();
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.COUNT, "2");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.type(0), "affect-observation");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.text(0), "She seemed distracted");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.entity(0), "Sarah");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.type(1), "intention");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.text(1), "Should bring David here");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.entity(1), "La Trattoria");
    }

    @Test
    void nullEntityIsOmitted() {
        String memoryId = memoryStore.store(MemoryInput.of(
            SUBJECT, ExperienceEvents.DOMAIN, TENANT, "Test"));

        var parsed = List.of(
            new SubThoughtExtractor.ParsedSubThought(
                SubThoughtTypes.SELF_REFLECTION, "I felt uneasy", null));

        extractor.applySubThoughts(memoryId, parsed, TENANT);

        var memory = memoryStore.query(
            MemoryQuery.forSubject(SUBJECT, ExperienceEvents.DOMAIN, TENANT)).getFirst();
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.COUNT, "1");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.type(0), "self-reflection");
        assertThat(memory.attributes()).doesNotContainKey(SubThoughtAttributeKeys.entity(0));
    }

    @Test
    void rejectsUnknownSubThoughtType() {
        String memoryId = memoryStore.store(MemoryInput.of(
            SUBJECT, ExperienceEvents.DOMAIN, TENANT, "Test"));

        var parsed = List.of(
            new SubThoughtExtractor.ParsedSubThought("bogus", "text", null));

        assertThatThrownBy(() -> extractor.applySubThoughts(memoryId, parsed, TENANT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown sub-thought type");
    }

    private static class NoOpEvent<T> implements jakarta.enterprise.event.Event<T> {
        @Override
        public void fire(T event)                                                                                                                                    {}

        @Override
        public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event)                                                                              {return java.util.concurrent.CompletableFuture.completedFuture(event);}

        @Override
        public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event, jakarta.enterprise.event.NotificationOptions options)                        {return java.util.concurrent.CompletableFuture.completedFuture(event);}

        @Override
        public jakarta.enterprise.event.Event<T> select(java.lang.annotation.Annotation... qualifiers)                                                               {return this;}

        @Override
        public <U extends T> jakarta.enterprise.event.Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers)                               {throw new UnsupportedOperationException();}

        @Override
        public <U extends T> jakarta.enterprise.event.Event<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {throw new UnsupportedOperationException();}
    }
}
