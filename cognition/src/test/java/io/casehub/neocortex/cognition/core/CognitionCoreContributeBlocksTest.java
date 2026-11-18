package io.casehub.neocortex.cognition.core;

import io.casehub.eidos.api.PromptTier;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.CognitiveLifecycle;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCoreContributeBlocksTest {

    private static CognitionCore createCore(CognitionConfig config) {
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
        return new CognitionCore(mood, drives, null, null, null, null, null, noOpHygiene,
                null, config, null);
    }

    @Test
    void contributeBlocksProducesCognitiveBlocks() {
        var config = CognitionConfig.none().with("mood", true);
        var core = createCore(config);
        core.tick("agent-1", "tenant-1", null, (aid, tid) -> Set.of());

        CognitiveLifecycle lifecycle = core;
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);
        var blocks = lifecycle.contributeBlocks(ctx);

        for (var block : blocks) {
            assertThat(block.tier()).isEqualTo(PromptTier.COGNITIVE);
            assertThat(block.tag()).isNotNull();
            assertThat(block.content()).isNotBlank();
        }
    }

    @Test
    void contributeBlocksReturnsEmptyWhenAllSectionsDisabled() {
        var config = CognitionConfig.none();
        var core = createCore(config);
        core.tick("agent-1", "tenant-1", null, (aid, tid) -> Set.of());

        CognitiveLifecycle lifecycle = core;
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);
        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).isEmpty();
    }

    @Test
    void contributeBlocksAlwaysReturnsCognitiveTierWithZeroSalience() {
        var config = CognitionConfig.none().with("mood", true).with("drives", true);
        var core = createCore(config);
        core.tick("agent-1", "tenant-1", null, (aid, tid) -> Set.of());

        CognitiveLifecycle lifecycle = core;
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);
        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).allSatisfy(block -> {
            assertThat(block.tier()).isEqualTo(PromptTier.COGNITIVE);
            assertThat(block.salience()).isBetween(0f, 1f);
        });
    }
}
