package io.casehub.neocortex.cognition.prompt.observation;

import io.casehub.neocortex.caps.SituationContext;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationResult;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationStrategy;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationTickParticipant;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CognitionObservationBuilderRegulationTest {

    private static final String AGENT = "a1";
    private static final String TENANT = "t1";

    @Test
    void renderIncludesRegulationNarrativeWhenPresent() {
        var store = new InMemoryMindMapStore();
        var core = mock(CognitionCore.class);
        var regulationParticipant = mock(EmotionRegulationTickParticipant.class);

        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.RESPONSE_MODULATION,
                List.of("anger"), List.of("gloating"),
                0.85, 0.4, 0.6,
                "You hold back the triumph", null, null);
        when(regulationParticipant.currentResult(AGENT, TENANT))
                .thenReturn(Optional.of(result));
        when(core.regulationParticipant()).thenReturn(regulationParticipant);
        when(core.config()).thenReturn(CognitionConfig.none().with("regulation", true));

        var drives = List.of(new DriveSnapshot("gloating", 0.7, "jaw tightens"));
        var builder = new CognitionObservationBuilder(AGENT, TENANT, store, core, null, drives);

        var section = builder.renderInternalState(
                List.of(), "", Set.of(), SituationContext.EMPTY, Map.of());

        assertThat(section).isNotNull();
        String text = ((ObservationSection.TextBlock) section).content();
        assertThat(text).contains("You hold back the triumph");
        assertThat(text).contains("RESPONSE_MODULATION");
    }

    @Test
    void renderUnchangedWhenNoRegulation() {
        var store = new InMemoryMindMapStore();
        var core = mock(CognitionCore.class);
        when(core.regulationParticipant()).thenReturn(null);

        var drives = List.of(new DriveSnapshot("gloating", 0.7, "jaw tightens"));
        var builder = new CognitionObservationBuilder(AGENT, TENANT, store, core, null, drives);

        var section = builder.renderInternalState(
                List.of(), "", Set.of(), SituationContext.EMPTY, Map.of());

        assertThat(section).isNotNull();
        String text = ((ObservationSection.TextBlock) section).content();
        assertThat(text).doesNotContain("Regulation");
    }
}
