package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.memory.seeding.biography.ActivityEntry;
import io.casehub.neocortex.memory.seeding.biography.RelationshipEntry.BdiValues;
import io.casehub.neocortex.memory.seeding.biography.BeliefEntry;
import io.casehub.neocortex.memory.seeding.biography.BiographyProfile;
import io.casehub.neocortex.memory.seeding.biography.CulturalContextEntry;
import io.casehub.neocortex.memory.seeding.biography.GoalEntry;
import io.casehub.neocortex.memory.seeding.biography.LifeEventEntry;
import io.casehub.neocortex.memory.seeding.biography.ProjectEntry;
import io.casehub.neocortex.memory.seeding.biography.RelationshipEntry;
import io.casehub.neocortex.memory.seeding.biography.GoalEntry.SubGoal;
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

    private BiographyProfile profileWithCulturalContexts(List<CulturalContextEntry> contexts) {
        return new BiographyProfile("agent-1", "t1",
                                    contexts, List.of(), List.of(), List.of(), List.of(),
                                    List.of(), List.of(), List.of(), List.of(), List.of(),
                                    List.of(), List.of(), List.of(), List.of());
    }

    private BiographyProfile profileWithActivities(List<ActivityEntry> activities) {
        return new BiographyProfile("agent-1", "t1",
                                    List.of(), List.of(), List.of(), List.of(), activities,
                                    List.of(), List.of(), List.of(), List.of(), List.of(),
                                    List.of(), List.of(), List.of(), List.of());
    }

    private BiographyProfile profileWithProjects(List<ProjectEntry> projects) {
        return new BiographyProfile("agent-1", "t1",
                                    List.of(), List.of(), List.of(), List.of(), List.of(),
                                    projects, List.of(), List.of(), List.of(), List.of(),
                                    List.of(), List.of(), List.of(), List.of());
    }

    private BiographyProfile profileWithRelationships(List<RelationshipEntry> relationships) {
        return new BiographyProfile("agent-1", "t1",
                                    List.of(), List.of(), List.of(), List.of(), List.of(),
                                    List.of(), List.of(), relationships, List.of(), List.of(),
                                    List.of(), List.of(), List.of(), List.of());
    }

    private BiographyProfile profileWithGoals(List<GoalEntry> goals) {
        return new BiographyProfile("agent-1", "t1",
                                    List.of(), List.of(), List.of(), List.of(), List.of(),
                                    List.of(), List.of(), List.of(), goals, List.of(),
                                    List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void stripsAnchorsFromCulturalContextDescription() {
        var entry = new CulturalContextEntry(
                "cc-1", null, "In Guillermo[Guillermo]'s workshop, art was everywhere.",
                List.of(), null, null);

        var profile = profileWithCulturalContexts(List.of(entry));
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");

        assertThat(cleaned.culturalContexts().getFirst().description())
                .isEqualTo("In Guillermo's workshop, art was everywhere.");
    }

    @Test
    void writesAnnotationsFromCulturalContext() {
        var entry = new CulturalContextEntry(
                "cc-2", null, "Guillermo[Guillermo] shaped the household.",
                List.of(), null, null);

        var profile = profileWithCulturalContexts(List.of(entry));
        preprocessor.preprocess(profile, "agent-1", "t1");
        preprocessor.afterImport("agent-1", "t1");

        var annotations = annotationIndex.findBySource("biography", "cc-2", "t1");
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().mentionText()).isEqualTo("Guillermo");
    }

    @Test
    void stripsAnchorsFromActivityNotes() {
        var entry = new ActivityEntry(
                "act-1", null, "Photography lesson", null, null, null, List.of(),
                "Guillermo[Guillermo] demonstrated darkroom techniques.", null);

        var profile = profileWithActivities(List.of(entry));
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");

        assertThat(cleaned.activities().getFirst().notes())
                .isEqualTo("Guillermo demonstrated darkroom techniques.");
    }

    @Test
    void stripsAnchorsFromProjectDescription() {
        var entry = new ProjectEntry(
                "proj-1", null, "Self-Portraits", "Frida[Frida] explored identity through self-portraits.",
                null, null, null);

        var profile = profileWithProjects(List.of(entry));
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");

        assertThat(cleaned.projects().getFirst().description())
                .isEqualTo("Frida explored identity through self-portraits.");
    }

    @Test
    void stripsAnchorsFromRelationshipBdiValues() {
        var bdi = new BdiValues(
                "Guillermo[Guillermo] is supportive.",
                "To learn from Guillermo[Guillermo].",
                "Will ask Guillermo[Guillermo] for guidance.");
        var entry = new RelationshipEntry(
                "rel-1", null, null, "Guillermo", List.of(), bdi, null, null, null, null);

        var profile = profileWithRelationships(List.of(entry));
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");

        var cleanedBdi = cleaned.relationships().getFirst().bdi();
        assertThat(cleanedBdi.beliefs()).isEqualTo("Guillermo is supportive.");
        assertThat(cleanedBdi.desires()).isEqualTo("To learn from Guillermo.");
        assertThat(cleanedBdi.intentions()).isEqualTo("Will ask Guillermo for guidance.");
    }

    @Test
    void stripsAnchorsFromGoalSubGoalDescription() {
        var subGoal = new SubGoal("sg-1", "short-term",
                                  "Ask Frida[Frida] to review the portfolio.");
        var entry = new GoalEntry(
                "goal-1", null, null, "Art portfolio", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                List.of(subGoal), null);

        var profile = profileWithGoals(List.of(entry));
        var cleaned = preprocessor.preprocess(profile, "agent-1", "t1");

        assertThat(cleaned.goals().getFirst().subGoals().getFirst().description())
                .isEqualTo("Ask Frida to review the portfolio.");
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
