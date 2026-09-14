package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.GraduationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGraduationClassifierTest {

    private final DefaultGraduationClassifier classifier = new DefaultGraduationClassifier();

    private Memory memory(String eventType, Map<String, String> extraAttrs) {
        var attrs = new java.util.HashMap<>(extraAttrs);
        attrs.put("event-type", eventType);
        return new Memory("m1", Subject.of("agent", "a1"),
            new MemoryDomain("experience"), "t1", null, "some event",
            attrs, Instant.now(), null,
            null, null, null, null, null);
    }

    @Test
    void classify_observation_producesBelief() {
        GraduationResult result = classifier.classify(
            memory("observation", Map.of("subject", "Bob")));
        assertEquals("belief", result.cognitiveKind());
        assertEquals(ConfidenceOrigin.STATED, result.confidenceOrigin());
        assertEquals("Bob", result.properties().get("subject"));
        assertEquals("active", result.properties().get("status"));
    }

    @Test
    void classify_action_producesIntention() {
        GraduationResult result = classifier.classify(
            memory("action", Map.of("capability", "persuade")));
        assertEquals("intention", result.cognitiveKind());
        assertEquals(ConfidenceOrigin.INFERRED, result.confidenceOrigin());
        assertEquals("persuade", result.properties().get("goal"));
        assertEquals("active", result.properties().get("status"));
    }

    @Test
    void classify_outcome_producesJudgment() {
        GraduationResult result = classifier.classify(
            memory("outcome", Map.of("result", "success")));
        assertEquals("judgment", result.cognitiveKind());
        assertEquals(ConfidenceOrigin.INFERRED, result.confidenceOrigin());
        assertEquals("success", result.properties().get("target"));
    }

    @Test
    void classify_unknownType_defaultsToBelief() {
        GraduationResult result = classifier.classify(
            memory("unknown", Map.of()));
        assertEquals("belief", result.cognitiveKind());
        assertEquals(ConfidenceOrigin.INFERRED, result.confidenceOrigin());
    }

    @Test
    void classify_observation_withoutSubject_stillProducesBelief() {
        GraduationResult result = classifier.classify(
            memory("observation", Map.of()));
        assertEquals("belief", result.cognitiveKind());
        assertFalse(result.properties().containsKey("subject"));
        assertEquals("active", result.properties().get("status"));
    }

    @Test
    void classify_action_withoutCapability_stillProducesIntention() {
        GraduationResult result = classifier.classify(
            memory("action", Map.of()));
        assertEquals("intention", result.cognitiveKind());
        assertFalse(result.properties().containsKey("goal"));
    }
}
