package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.regulation.RegulationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCoreRegulationContextTest {

    @Test
    void tickWithRegulationContextPassesItToParticipants() {
        var config = CognitionConfig.none();
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
        var core = new CognitionCore(mood, drives, null, null, null, null, null, noOpHygiene);

        var capturedContext = new AtomicReference<CognitionTickContext>();
        core.addParticipant(CognitionPhase.FOUNDATION, ctx -> capturedContext.set(ctx));

        var regCtx = new RegulationContext("library", List.of("james"), "investigation");
        core.tick("agent1", "tenant1", null, (a, t) -> Set.of(),
                  "observation", null, regCtx);

        assertThat(capturedContext.get()).isNotNull();
        assertThat(capturedContext.get().regulationContext()).isNotNull();
        assertThat(capturedContext.get().regulationContext().location()).isEqualTo("library");
        assertThat(capturedContext.get().regulationContext().interactingAgents())
                .containsExactly("james");
    }

    @Test
    void tickWithoutRegulationContextPassesNull() {
        var config = CognitionConfig.none();
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
        var core = new CognitionCore(mood, drives, null, null, null, null, null, noOpHygiene);

        var capturedContext = new AtomicReference<CognitionTickContext>();
        core.addParticipant(CognitionPhase.FOUNDATION, ctx -> capturedContext.set(ctx));

        core.tick("agent1", "tenant1", null, (a, t) -> Set.of(),
                  "observation", null);

        assertThat(capturedContext.get()).isNotNull();
        assertThat(capturedContext.get().regulationContext()).isNull();
    }
}
