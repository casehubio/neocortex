package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.prompt.CognitiveLifecycle;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCoreCognitiveLifecycleTest {

    private static CognitionCore createMinimalCore() {
        var mood = new io.casehub.neocortex.cognition.mood.MoodOrchestrator(
                io.casehub.neocortex.cognition.mood.MoodConfig.defaults());
        var noOpHygiene = new io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator() {
            @Override public void tick(String a, String t) {}
            @Override public io.casehub.neocortex.memory.KnowledgeGapSummary knowledgeGaps(String a, String t) {
                return io.casehub.neocortex.memory.KnowledgeGapSummary.empty();
            }
        };
        var drives = new io.casehub.neocortex.cognition.drive.DriveOrchestrator(
                new io.casehub.neocortex.cognition.drive.CuriosityDrive(null, null, 0.0),
                new io.casehub.neocortex.cognition.drive.CompetenceDrive(null),
                new io.casehub.neocortex.cognition.drive.AffiliationDrive(null, 0.3, java.time.Duration.ofHours(1)),
                new io.casehub.neocortex.cognition.drive.AutonomyDrive(null, 0.5),
                mood,
                new io.casehub.neocortex.cognition.drive.DriveComposer(),
                io.casehub.neocortex.cognition.drive.DriveConfig.defaults());
        return new CognitionCore(mood, drives, null, null, null, null, null, noOpHygiene);
    }

    @Test
    void implementsCognitiveLifecycle() {
        assertThat(CognitiveLifecycle.class).isAssignableFrom(CognitionCore.class);
    }

    @Test
    void lifecycleTickDelegatesToCoreTick() {
        var core = createMinimalCore();

        CognitiveLifecycle lifecycle = core;
        lifecycle.tick("agent-1", "tenant-1", Set.of());

        var sections = lifecycle.promptSections();
        assertThat(sections).isNotNull();
    }

    @Test
    void lifecycleRecordInteractionDelegatesToCore() {
        var core = createMinimalCore();
        core.tick("agent-1", "tenant-1", null, (aid, tid) -> Set.of());

        CognitiveLifecycle lifecycle = core;
        lifecycle.recordInteraction("agent-1", "tenant-1", null, "hello", "hi there");
    }
}
