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

    @Test
    void extractFromTextClassifiesAffect() {
        var result = extractor.extractFromText("She seemed really upset today.");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().type()).isEqualTo(SubThoughtTypes.AFFECT_OBSERVATION);
        assertThat(result.getFirst().text()).isEqualTo("She seemed really upset today.");
        assertThat(result.getFirst().confidence()).isEqualTo(0.8);
    }

    @Test
    void extractFromTextClassifiesMultipleSentences() {
        var result = extractor.extractFromText("She seemed sad. I should help her. The weather was nice.");
        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(SubThoughtTypes.AFFECT_OBSERVATION);
        assertThat(result.get(1).type()).isEqualTo(SubThoughtTypes.INTENTION);
    }

    @Test
    void extractFromTextReturnsEmptyForNull() {
        assertThat(extractor.extractFromText(null)).isEmpty();
    }

    @Test
    void extractFromTextReturnsEmptyForBlank() {
        assertThat(extractor.extractFromText("  ")).isEmpty();
    }

    @Test
    void extractFromTextSkipsUnmatchedSentences() {
        var result = extractor.extractFromText("The weather was nice. We walked to the park.");
        assertThat(result).isEmpty();
    }

    @Test
    void onExtractionRequestedEnrichesAndFiresEvent() {
        String memoryId = memoryStore.store(MemoryInput.of(
                SUBJECT, ExperienceEvents.DOMAIN, TENANT, "She seemed distracted."));

        var recording          = new RecordingEvent<SubThoughtsEnriched>();
        var enrichingExtractor = new SubThoughtExtractor(memoryStore, recording);

        enrichingExtractor.onExtractionRequested(new SubThoughtExtractionRequested(
                memoryId, TENANT, "She seemed distracted.",
                io.casehub.platform.api.identity.PrincipalId.agent("a1")));

        var memory = memoryStore.query(
                MemoryQuery.forSubject(SUBJECT, ExperienceEvents.DOMAIN, TENANT)).getFirst();
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.COUNT, "1");
        assertThat(memory.attributes()).containsEntry(SubThoughtAttributeKeys.type(0), SubThoughtTypes.AFFECT_OBSERVATION);

        assertThat(recording.fired).hasSize(1);
        assertThat(recording.fired.getFirst().memoryId()).isEqualTo(memoryId);
        assertThat(recording.fired.getFirst().agentId()).isEqualTo("a1");
    }

    private static class RecordingEvent<T> implements jakarta.enterprise.event.Event<T> {
        final java.util.List<T> fired = new java.util.ArrayList<>();

        @Override
        public void fire(T event) {fired.add(event);}

        @Override
        public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event) {return java.util.concurrent.CompletableFuture.completedFuture(event);}

        @Override
        public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event, jakarta.enterprise.event.NotificationOptions options) {return java.util.concurrent.CompletableFuture.completedFuture(event);}

        @Override
        public jakarta.enterprise.event.Event<T> select(java.lang.annotation.Annotation... qualifiers) {return this;}

        @Override
        public <U extends T> jakarta.enterprise.event.Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {throw new UnsupportedOperationException();}

        @Override
        public <U extends T> jakarta.enterprise.event.Event<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {throw new UnsupportedOperationException();}
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
