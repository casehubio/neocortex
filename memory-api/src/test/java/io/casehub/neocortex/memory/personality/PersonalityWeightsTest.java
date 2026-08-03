package io.casehub.neocortex.memory.personality;

import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PersonalityWeightsTest {

    @Test
    void getWeightReturnsConfiguredWeight() {
        var domain = new MemoryDomain("relationship");
        var weights = new PersonalityWeights(Map.of(domain, 1.5));
        assertEquals(1.5, weights.getWeight(domain));
    }

    @Test
    void getWeightReturnsOneForUnconfiguredDomain() {
        var weights = new PersonalityWeights(Map.of(new MemoryDomain("relationship"), 1.5));
        assertEquals(1.0, weights.getWeight(new MemoryDomain("experience")));
    }

    @Test
    void rejectsNullDomainWeights() {
        assertThrows(NullPointerException.class, () -> new PersonalityWeights(null));
    }

    @Test
    void rejectsZeroWeight() {
        assertThrows(IllegalArgumentException.class, () ->
            new PersonalityWeights(Map.of(new MemoryDomain("relationship"), 0.0)));
    }

    @Test
    void rejectsNegativeWeight() {
        assertThrows(IllegalArgumentException.class, () ->
            new PersonalityWeights(Map.of(new MemoryDomain("relationship"), -0.5)));
    }

    @Test
    void defensivelyCopies() {
        var mutable = new java.util.HashMap<>(Map.of(new MemoryDomain("relationship"), 1.5));
        var weights = new PersonalityWeights(mutable);
        mutable.put(new MemoryDomain("experience"), 2.0);
        assertEquals(1.0, weights.getWeight(new MemoryDomain("experience")));
    }

    @Test
    void acceptsEmptyMap() {
        var weights = new PersonalityWeights(Map.of());
        assertEquals(1.0, weights.getWeight(new MemoryDomain("anything")));
    }
}
