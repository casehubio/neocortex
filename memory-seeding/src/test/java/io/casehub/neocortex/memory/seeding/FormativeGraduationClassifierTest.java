package io.casehub.neocortex.memory.seeding;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.FormativeAttributeKeys;
import io.casehub.neocortex.memory.experience.GraduationClassifier;
import io.casehub.neocortex.memory.experience.GraduationResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FormativeGraduationClassifierTest {

    private final GraduationClassifier delegate = mock(GraduationClassifier.class);
    private final FormativeGraduationClassifier classifier = new FormativeGraduationClassifier(delegate);

    @Test
    void formativeEventClassifiedWithMetadata() {
        var attrs = new HashMap<String, String>();
        attrs.put(ExperienceAttributeKeys.EVENT_TYPE, "formative");
        attrs.put(FormativeAttributeKeys.CATALOGUE_ENTRY_ID, "attachment-anxious");
        attrs.put(FormativeAttributeKeys.SITUATION_TYPES, "inconsistent_care,rejection");
        attrs.put(FormativeAttributeKeys.SALIENCE_MULTIPLIER, "3.0");
        attrs.put(FormativeAttributeKeys.DEVELOPMENTAL_PERIOD, "infancy");
        attrs.put(FormativeAttributeKeys.REINFORCEMENT_SCHEDULE, "variable_ratio");

        var memory = mock(Memory.class);
        when(memory.attributes()).thenReturn(attrs);

        var result = classifier.classify(memory);

        assertThat(result.cognitiveKind()).isEqualTo("formative-experience");
        assertThat(result.confidenceOrigin()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(result.properties())
            .containsEntry("catalogue-entry-id", "attachment-anxious")
            .containsEntry("situation-types", "inconsistent_care,rejection")
            .containsEntry("salience-multiplier", "3.0")
            .containsEntry("developmental-period", "infancy")
            .containsEntry("reinforcement-schedule", "variable_ratio");
        verifyNoInteractions(delegate);
    }

    @Test
    void formativeEventMissingOptionalFieldsStillClassifies() {
        var attrs = new HashMap<String, String>();
        attrs.put(ExperienceAttributeKeys.EVENT_TYPE, "formative");
        attrs.put(FormativeAttributeKeys.CATALOGUE_ENTRY_ID, "entry1");

        var memory = mock(Memory.class);
        when(memory.attributes()).thenReturn(attrs);

        var result = classifier.classify(memory);

        assertThat(result.cognitiveKind()).isEqualTo("formative-experience");
        assertThat(result.properties())
            .containsEntry("catalogue-entry-id", "entry1")
            .doesNotContainKey("situation-types");
    }

    @Test
    void runtimeEventDelegatesToDefault() {
        var memory = mock(Memory.class);
        when(memory.attributes()).thenReturn(
            Map.of(ExperienceAttributeKeys.EVENT_TYPE, "observation"));
        var expected = new GraduationResult("belief", ConfidenceOrigin.STATED, Map.of());
        when(delegate.classify(memory)).thenReturn(expected);

        var result = classifier.classify(memory);

        assertThat(result).isEqualTo(expected);
        verify(delegate).classify(memory);
    }
}
