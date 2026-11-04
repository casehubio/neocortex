package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntityOverlapModulationFactorTest {

    private Memory memory(String memoryId, String text) {
        return new Memory(memoryId, Subject.of("agent", "a1"),
                          new MemoryDomain("experience"), "t1", null, text,
                          java.util.Map.of(), Instant.now(), null, null, null, null, null, null);
    }

    @Test
    void boostsWhenEntitiesOverlap() {
        var mem = memory("mem-1", "{{node-alice:full:Alice}} and {{node-bob:full:Bob}} talked.");

        var factor = new EntityOverlapModulationFactor(Set.of("node-alice", "node-charlie"));

        double result = factor.apply(mem, null);

        assertThat(result).isEqualTo(1.15);
    }

    @Test
    void boostsProportionalToOverlapCount() {
        var mem = memory("mem-1", "{{node-alice:full:Alice}} met {{node-bob:full:Bob}}.");

        var factor = new EntityOverlapModulationFactor(Set.of("node-alice", "node-bob"));

        double result = factor.apply(mem, null);

        assertThat(result).isEqualTo(1.3);
    }

    @Test
    void noBoostWhenNoOverlap() {
        var mem = memory("mem-1", "{{node-alice:full:Alice}} was here.");

        var factor = new EntityOverlapModulationFactor(Set.of("node-charlie"));

        double result = factor.apply(mem, null);

        assertThat(result).isEqualTo(1.0);
    }

    @Test
    void noBoostWhenNoTokens() {
        var mem = memory("mem-1", "Plain text with no tokens.");

        var factor = new EntityOverlapModulationFactor(Set.of("node-alice"));

        double result = factor.apply(mem, null);

        assertThat(result).isEqualTo(1.0);
    }

    @Test
    void noBoostWhenContextEmpty() {
        var mem = memory("mem-1", "{{node-alice:full:Alice}} was here.");

        var factor = new EntityOverlapModulationFactor(Set.of());

        double result = factor.apply(mem, null);

        assertThat(result).isEqualTo(1.0);
    }
}
