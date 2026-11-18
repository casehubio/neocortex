package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void shouldDefaultAgentIdToNull() {
        assertNull(MutationContext.agentId());
    }

    @Test
    void shouldSetAndGetAgentId() {
        MutationContext.setAgent("agent-42");
        assertEquals("agent-42", MutationContext.agentId());
    }

    @Test
    void shouldClearAgentId() {
        MutationContext.setAgent("agent-42");
        MutationContext.clear();
        assertNull(MutationContext.agentId());
    }

    @Test
    void shouldSetAgentIdIndependentlyOfSource() {
        MutationContext.set("consolidation:phase");
        MutationContext.setAgent("agent-7");
        assertEquals("consolidation:phase", MutationContext.get());
        assertEquals("agent-7", MutationContext.agentId());
    }
}
