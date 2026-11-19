package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.trace.PipelineTrace.SituationalPersonalitySnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SituationalSnapshotAccessorTest {

    @Test
    void noOpReturnsEmpty() {
        SituationalSnapshotAccessor accessor = (agentId, tenantId) -> Optional.empty();
        assertThat(accessor.currentSnapshot("agent-1", "tenant-1")).isEmpty();
    }

    @Test
    void returnsSnapshotWhenAvailable() {
        var snapshot = new SituationalPersonalitySnapshot(
            Map.of("STRESS", 0.72),
            "STRESS", null,
            Map.of("anxiousness", 0.15, "emotional-lability", 0.12),
            2
        );
        SituationalSnapshotAccessor accessor = (agentId, tenantId) -> Optional.of(snapshot);
        var result = accessor.currentSnapshot("agent-1", "tenant-1");
        assertThat(result).isPresent();
        assertThat(result.get().affectiveWinner()).isEqualTo("STRESS");
    }
}
