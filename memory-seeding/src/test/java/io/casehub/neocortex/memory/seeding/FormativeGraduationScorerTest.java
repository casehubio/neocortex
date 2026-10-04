package io.casehub.neocortex.memory.seeding;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.GraduationContext;
import io.casehub.neocortex.memory.experience.GraduationScorer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FormativeGraduationScorerTest {

    private final GraduationScorer delegate = mock(GraduationScorer.class);
    private final FormativeGraduationScorer scorer = new FormativeGraduationScorer(delegate);

    @Test
    void formativeEventBypassesCorroboration() {
        var memory = mockMemory("formative", 0.8);
        var context = new GraduationContext(0, 0, "tenant1");

        double score = scorer.score(memory, context);

        assertThat(score).isEqualTo(0.8);
        verifyNoInteractions(delegate);
    }

    @Test
    void formativeEventWithNullConfidenceDefaults() {
        var memory = mockMemory("formative", null);
        var context = new GraduationContext(0, 0, "tenant1");

        double score = scorer.score(memory, context);

        assertThat(score).isEqualTo(0.8);
    }

    @Test
    void runtimeEventDelegatesToDefault() {
        var memory = mockMemory("observation", 0.7);
        var context = new GraduationContext(5, 0, "tenant1");
        when(delegate.score(memory, context)).thenReturn(0.7);

        double score = scorer.score(memory, context);

        assertThat(score).isEqualTo(0.7);
        verify(delegate).score(memory, context);
    }

    private Memory mockMemory(String eventType, Double confidenceValue) {
        var memory = mock(Memory.class);
        when(memory.attributes()).thenReturn(
            Map.of(ExperienceAttributeKeys.EVENT_TYPE, eventType));
        if (confidenceValue != null) {
            var conf = Confidence.unknown(confidenceValue);
            when(memory.confidence()).thenReturn(conf);
        }
        return memory;
    }
}
