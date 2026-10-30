package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.memory.seeding.biography.BeliefEntry;
import io.casehub.neocortex.memory.seeding.biography.BiographyProfile;
import io.casehub.neocortex.memory.seeding.biography.LifeEventEntry;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnchorBiographyPreprocessorTest {

    private InMemoryEntityAnnotationIndex annotationIndex;
    private InMemoryMindMapStore mindMapStore;
    private AnchorBiographyPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        annotationIndex = new InMemoryEntityAnnotationIndex();
        mindMapStore = new InMemoryMindMapStore();
        preprocessor = new AnchorBiographyPreprocessor(annotationIndex, mindMapStore);

        String sgId = mindMapStore.createSubgraph(
            new SubgraphInput("General", SubgraphTypes.GENERAL, null), "t1");
        mindMapStore.addNode(NodeInput.of("Guillermo", sgId), "t1");
        mindMapStore.addNode(NodeInput.of("Frida", sgId), "t1");
    }

    private BiographyProfile profileWith(List<LifeEventEntry> lifeEvents,
                                          List<BeliefEntry> beliefs) {
        return new BiographyProfile("agent-1", "t1",
            List.of(), List.of(), lifeEvents, List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), beliefs,
            List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void stripsAnchorsFromLifeEventDescription() {
        var entry = new LifeEventEntry(
            "le-1", null, null, null,
            "Guillermo[Guillermo] taught Frida[Frida] photography.",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

        var profile = profileWith(List.of(entry), List.of());
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");

        assertThat(cleaned.lifeEvents().getFirst().description())
            .isEqualTo("Guillermo taught Frida photography.");
    }

    @Test
    void writesAnnotationsAfterImport() {
        var entry = new LifeEventEntry(
            "le-1", null, null, null,
            "Guillermo[Guillermo] taught Frida[Frida] photography.",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

        var profile = profileWith(List.of(entry), List.of());
        preprocessor.preprocess(profile, "agent-1", "t1");
        preprocessor.afterImport("agent-1", "t1");

        List<EntityAnnotation> annotations = annotationIndex.findBySource("biography", "le-1", "t1");
        assertThat(annotations).hasSize(2);
        assertThat(annotations).extracting(EntityAnnotation::mentionText)
            .containsExactlyInAnyOrder("Guillermo", "Frida");
        assertThat(annotations).allMatch(a -> a.method().equals("yaml-anchor"));
    }

    @Test
    void stripsAnchorsFromBeliefDescription() {
        var entry = new BeliefEntry(
            "be-1", null, null, null,
            "Guillermo[Guillermo] was always supportive.",
            null, null, null, null);

        var profile = profileWith(List.of(), List.of(entry));
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");

        assertThat(cleaned.beliefs().getFirst().description())
            .isEqualTo("Guillermo was always supportive.");
    }

    @Test
    void noAnchorsPassesThrough() {
        var entry = new LifeEventEntry(
            "le-2", null, null, null,
            "A quiet afternoon in the garden.",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

        var profile = profileWith(List.of(entry), List.of());
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");

        assertThat(cleaned.lifeEvents().getFirst().description())
            .isEqualTo("A quiet afternoon in the garden.");
    }

    @Test
    void unresolvedAliasSkippedGracefully() {
        var entry = new LifeEventEntry(
            "le-3", null, null, null,
            "Mystery[unknown] appeared at the door.",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

        var profile = profileWith(List.of(entry), List.of());
        preprocessor.preprocess(profile, "agent-1", "t1");
        preprocessor.afterImport("agent-1", "t1");

        List<EntityAnnotation> annotations = annotationIndex.findBySource("biography", "le-3", "t1");
        assertThat(annotations).isEmpty();
    }

    @Test
    void annotationOffsetsCorrectInCleanText() {
        var entry = new LifeEventEntry(
            "le-4", null, null, null,
            "Guillermo[Guillermo] and Frida[Frida] met.",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

        var profile = profileWith(List.of(entry), List.of());
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");
        preprocessor.afterImport("agent-1", "t1");

        String cleanText = cleaned.lifeEvents().getFirst().description();
        List<EntityAnnotation> annotations = annotationIndex.findBySource("biography", "le-4", "t1");

        for (EntityAnnotation a : annotations) {
            assertThat(cleanText.substring(a.startOffset(), a.endOffset()))
                .isEqualTo(a.mentionText());
        }
    }
}
