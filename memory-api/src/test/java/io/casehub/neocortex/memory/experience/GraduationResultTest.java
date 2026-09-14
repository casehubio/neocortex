package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraduationResultTest {

    @Test
    void requiresCognitiveKind() {
        assertThrows(NullPointerException.class,
            () -> new GraduationResult(null, ConfidenceOrigin.STATED, Map.of()));
    }

    @Test
    void requiresConfidenceOrigin() {
        assertThrows(NullPointerException.class,
            () -> new GraduationResult("belief", null, Map.of()));
    }

    @Test
    void nullPropertiesDefaultsToEmpty() {
        var result = new GraduationResult("belief", ConfidenceOrigin.STATED, null);
        assertEquals(Map.of(), result.properties());
    }

    @Test
    void preservesAllFields() {
        var props = Map.of("subject", "Bob");
        var result = new GraduationResult("judgment", ConfidenceOrigin.INFERRED, props);
        assertEquals("judgment", result.cognitiveKind());
        assertEquals(ConfidenceOrigin.INFERRED, result.confidenceOrigin());
        assertEquals("Bob", result.properties().get("subject"));
    }
}
