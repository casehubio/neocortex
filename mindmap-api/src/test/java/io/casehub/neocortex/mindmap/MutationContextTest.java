package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MutationContextTest {

    @AfterEach
    void cleanup() {
        MutationContext.clear();
    }

    @Test
    void shouldDefaultToManual() {
        assertEquals("manual", MutationContext.get());
    }

    @Test
    void shouldSetAndGet() {
        MutationContext.set("consolidation:MergeDetectionPhase");
        assertEquals("consolidation:MergeDetectionPhase", MutationContext.get());
    }

    @Test
    void shouldClearToDefault() {
        MutationContext.set("conversation-bridge");
        MutationContext.clear();
        assertEquals("manual", MutationContext.get());
    }
}
