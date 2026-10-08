package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SubThoughtSituationDecoratorTest {

    @Test
    void addsConcernActivationsToBaseResult() {
        SituationClassifier base = (desc, meta) -> List.of(
            new SituationActivation("social_threat", 0.3)
        );
        var decorator = new SubThoughtSituationDecorator(base);
        var result = decorator.classify("worried about her", Map.of("cognitiveKind", "concern"));
        assertTrue(result.size() >= 2);
        var socialThreat = result.stream()
            .filter(a -> "social_threat".equals(a.nodeId())).findFirst().orElseThrow();
        assertEquals(0.6, socialThreat.confidence());
        assertTrue(result.stream().anyMatch(a -> "psychological_threat".equals(a.nodeId())));
    }

    @Test
    void mergesTakesMaxConfidence() {
        SituationClassifier base = (desc, meta) -> List.of(
            new SituationActivation("social_threat", 0.8)
        );
        var decorator = new SubThoughtSituationDecorator(base);
        var result = decorator.classify("worried", Map.of("cognitiveKind", "concern"));
        var socialThreat = result.stream()
            .filter(a -> "social_threat".equals(a.nodeId())).findFirst().orElseThrow();
        assertEquals(0.8, socialThreat.confidence());
    }

    @Test
    void passesThoughWithoutCognitiveKind() {
        SituationClassifier base = (desc, meta) -> List.of(
            new SituationActivation("mastery", 0.5)
        );
        var decorator = new SubThoughtSituationDecorator(base);
        var result = decorator.classify("did well", Map.of());
        assertEquals(1, result.size());
        assertEquals("mastery", result.getFirst().nodeId());
    }

    @Test
    void passesThoughWithNullMetadata() {
        SituationClassifier base = (desc, meta) -> List.of(
            new SituationActivation("mastery", 0.5)
        );
        var decorator = new SubThoughtSituationDecorator(base);
        var result = decorator.classify("did well", null);
        assertEquals(1, result.size());
    }

    @Test
    void addsIntentionActivations() {
        SituationClassifier base = (desc, meta) -> List.of();
        var decorator = new SubThoughtSituationDecorator(base);
        var result = decorator.classify("plan to do it", Map.of("cognitiveKind", "intention"));
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> "agency_granted".equals(a.nodeId())));
        assertTrue(result.stream().anyMatch(a -> "choice_available".equals(a.nodeId())));
    }

    @Test
    void unknownCognitiveKindPassesThrough() {
        SituationClassifier base = (desc, meta) -> List.of(
            new SituationActivation("mastery", 0.5)
        );
        var decorator = new SubThoughtSituationDecorator(base);
        var result = decorator.classify("text", Map.of("cognitiveKind", "unknown-type"));
        assertEquals(1, result.size());
    }
}
