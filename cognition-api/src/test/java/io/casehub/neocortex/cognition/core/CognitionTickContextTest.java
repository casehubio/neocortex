package io.casehub.neocortex.cognition.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CognitionTickContextTest {

    private static final SubjectResolver NOOP_RESOLVER = (agentId, tenantId) -> java.util.Set.of();

    @Test
    void canonicalConstructor_acceptsTargetAgentId() {
        var ctx = new CognitionTickContext("agent-1", "tenant-1", null, NOOP_RESOLVER,
                "observation", null, "target-agent");
        assertThat(ctx.targetAgentId()).isEqualTo("target-agent");
        assertThat(ctx.observation()).isEqualTo("observation");
    }

    @Test
    void fourArgConstructor_targetAgentIdIsNull() {
        var ctx = new CognitionTickContext("agent-1", "tenant-1", null, NOOP_RESOLVER);
        assertThat(ctx.targetAgentId()).isNull();
        assertThat(ctx.observation()).isNull();
        assertThat(ctx.briefing()).isNull();
    }

    @Test
    void fiveArgConstructor_targetAgentIdIsNull() {
        var ctx = new CognitionTickContext("agent-1", "tenant-1", null, NOOP_RESOLVER, "obs");
        assertThat(ctx.targetAgentId()).isNull();
        assertThat(ctx.observation()).isEqualTo("obs");
    }

    @Test
    void nullTargetAgentId_isAllowed() {
        var ctx = new CognitionTickContext("agent-1", "tenant-1", null, NOOP_RESOLVER,
                "obs", null, null);
        assertThat(ctx.targetAgentId()).isNull();
    }
}
