package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.trace.PipelineTrace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowArchetypePromptSectionTest {

    @Test
    void rendersAffectiveModeWhenActive() {
        var snapshot = new PipelineTrace.SituationalPersonalitySnapshot(
                Map.of("STRESS", 0.72, "FLOW", 0.1),
                "STRESS", null, Map.of("Anxiousness", 0.15), 1);

        var section = new ShadowArchetypePromptSection((a, t) -> Optional.of(snapshot));
        var ctx = new CognitionRenderContext("agent1", "t1", null, List.of());

        var text = section.render(ctx);

        assertThat(text).isNotNull();
        assertThat(text).contains("stress");
        assertThat(text).contains("72%");
        assertThat(text).contains("1 personality trait has shifted");
        assertThat(text).contains("do not comment on or explain it");
    }

    @Test
    void rendersSocialModeWhenActive() {
        var snapshot = new PipelineTrace.SituationalPersonalitySnapshot(
                Map.of("SOCIAL_COMPETITIVE", 0.65),
                null, "SOCIAL_COMPETITIVE", Map.of(), 0);

        var section = new ShadowArchetypePromptSection((a, t) -> Optional.of(snapshot));
        var ctx = new CognitionRenderContext("agent1", "t1", null, List.of());

        var text = section.render(ctx);

        assertThat(text).isNotNull();
        assertThat(text).contains("social-competitive");
    }

    @Test
    void returnsNullWhenNoSnapshotAvailable() {
        var section = new ShadowArchetypePromptSection((a, t) -> Optional.empty());
        var ctx = new CognitionRenderContext("agent1", "t1", null, List.of());

        assertThat(section.render(ctx)).isNull();
    }

    @Test
    void returnsNullWhenNoActiveWinner() {
        var snapshot = new PipelineTrace.SituationalPersonalitySnapshot(
                Map.of("STRESS", 0.1), null, null, Map.of(), 0);

        var section = new ShadowArchetypePromptSection((a, t) -> Optional.of(snapshot));
        var ctx = new CognitionRenderContext("agent1", "t1", null, List.of());

        assertThat(section.render(ctx)).isNull();
    }

    @Test
    void blockTagIsSituational() {
        var section = new ShadowArchetypePromptSection((a, t) -> Optional.empty());
        assertThat(section.blockTag()).isEqualTo(BlockTag.SITUATIONAL);
    }
}
