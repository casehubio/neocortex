package io.casehub.neocortex.memory.mood;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MoodDecayTest {

    private static final MoodBaseline NEUTRAL = new MoodBaseline(0.0, 0.0, 0.0);
    private static final Duration TAU = Duration.ofHours(6);

    private MoodState mood(double p, double a, double d) {
        return new MoodState("a1", "t1", null, p, a, d, "test", null, Map.of());
    }

    @Test
    void decaysTowardBaseline() {
        var current = mood(0.8, -0.6, 0.4);
        var decayed = MoodDecay.decay(current, NEUTRAL, Duration.ofHours(6), TAU);
        assertTrue(Math.abs(decayed.pleasure()) < 0.8);
        assertTrue(Math.abs(decayed.arousal()) < 0.6);
        assertTrue(Math.abs(decayed.dominance()) < 0.4);
    }

    @Test
    void convergesOverLongDuration() {
        var current = mood(1.0, -1.0, 0.5);
        var decayed = MoodDecay.decay(current, NEUTRAL, Duration.ofDays(30), TAU);
        assertEquals(0.0, decayed.pleasure(), 0.001);
        assertEquals(0.0, decayed.arousal(), 0.001);
        assertEquals(0.0, decayed.dominance(), 0.001);
    }

    @Test
    void zeroElapsedReturnsCurrentValues() {
        var current = mood(0.5, -0.3, 0.7);
        var decayed = MoodDecay.decay(current, NEUTRAL, Duration.ZERO, TAU);
        assertEquals(0.5, decayed.pleasure(), 0.0001);
        assertEquals(-0.3, decayed.arousal(), 0.0001);
        assertEquals(0.7, decayed.dominance(), 0.0001);
    }

    @Test
    void negativeElapsedTreatedAsZero() {
        var current = mood(0.5, -0.3, 0.7);
        var decayed = MoodDecay.decay(current, NEUTRAL, Duration.ofHours(-1), TAU);
        assertEquals(0.5, decayed.pleasure(), 0.0001);
    }

    @Test
    void alreadyAtBaselineStaysAtBaseline() {
        var baseline = new MoodBaseline(0.3, -0.2, 0.1);
        var current = mood(0.3, -0.2, 0.1);
        var decayed = MoodDecay.decay(current, baseline, Duration.ofHours(10), TAU);
        assertEquals(0.3, decayed.pleasure(), 0.0001);
        assertEquals(-0.2, decayed.arousal(), 0.0001);
        assertEquals(0.1, decayed.dominance(), 0.0001);
    }

    @Test
    void decaysTowardNonZeroBaseline() {
        var baseline = new MoodBaseline(0.5, 0.5, 0.5);
        var current = mood(-0.5, -0.5, -0.5);
        var decayed = MoodDecay.decay(current, baseline, Duration.ofHours(6), TAU);
        assertTrue(decayed.pleasure() > -0.5);
        assertTrue(decayed.arousal() > -0.5);
        assertTrue(decayed.dominance() > -0.5);
        assertTrue(decayed.pleasure() < 0.5);
    }

    @Test
    void higherTimeConstantSlowsDecay() {
        var current = mood(0.8, 0.0, 0.0);
        var elapsed = Duration.ofHours(6);
        var fast = MoodDecay.decay(current, NEUTRAL, elapsed, Duration.ofHours(3));
        var slow = MoodDecay.decay(current, NEUTRAL, elapsed, Duration.ofHours(12));
        assertTrue(fast.pleasure() < slow.pleasure());
    }

    @Test
    void preservesAgentAndTenantId() {
        var current = new MoodState("agent-x", "tenant-y", null, 0.8, 0.0, 0.0,
                                    "event", "turn-1", Map.of("k", "v"));
        var decayed = MoodDecay.decay(current, NEUTRAL, Duration.ofHours(1), TAU);
        assertEquals("agent-x", decayed.agentId());
        assertEquals("tenant-y", decayed.tenantId());
        assertEquals("decay", decayed.cause());
    }
}
