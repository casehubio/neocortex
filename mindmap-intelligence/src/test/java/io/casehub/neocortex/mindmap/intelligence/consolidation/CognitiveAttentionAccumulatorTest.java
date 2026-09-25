package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.CognitiveAttentionRequired;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveAttentionAccumulatorTest {

    @Test
    void signals_accumulate_per_principal() {
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("agent-a"),
            CognitiveDefaults.empty("agent-b"));
        var acc = new CognitiveAttentionAccumulator(
            registry, null, fired::add, Clock.systemUTC(), 5.0, 0);

        acc.addSignals(List.of(
            new AttentionSignal("agent-a", "t1", SignalCategory.URGENCY_SPIKE,
                "n1", "G1", 3.0, "r1"),
            new AttentionSignal("agent-b", "t1", SignalCategory.DECAY_DETECTED,
                "n2", "G2", 2.0, "r2")));

        assertTrue(fired.isEmpty());

        acc.addSignals(List.of(
            new AttentionSignal("agent-a", "t1", SignalCategory.PRIORITY_SHIFT,
                "n3", "G3", 3.0, "r3")));

        assertEquals(1, fired.size());
        assertEquals("agent-a", fired.get(0).briefing().principalId());
    }

    @Test
    void adaptive_threshold_lowers_with_high_urgency_p75() {
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("a"));
        var acc = new CognitiveAttentionAccumulator(
            registry, null, fired::add, Clock.systemUTC(), 5.0, 0);

        acc.updateUrgencyP75("a", 0.8);
        // adjusted = 5.0 * (1.0 - 0.2) = 4.0
        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE,
                "n1", "G1", 4.5, "r")));
        assertEquals(1, fired.size());
    }

    @Test
    void minimum_interval_suppresses_rapid_pushes() {
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var fixedClock = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("a"));
        var acc = new CognitiveAttentionAccumulator(
            registry, null, fired::add, fixedClock, 5.0, 300);

        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE,
                "n1", "G1", 6.0, "r")));
        assertEquals(1, fired.size());

        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.DECAY_DETECTED,
                "n2", "G2", 6.0, "r")));
        assertEquals(1, fired.size(), "second push suppressed by interval guard");
    }

    @Test
    void deduplication_keeps_higher_significance() {
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("a"));
        var acc = new CognitiveAttentionAccumulator(
            registry, null, fired::add, Clock.systemUTC(), 5.0, 0);

        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE,
                "n1", "G1", 2.0, "low")));
        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE,
                "n1", "G1", 4.0, "high"),
            new AttentionSignal("a", "t", SignalCategory.DECAY_DETECTED,
                "n2", "G2", 2.0, "other")));

        assertEquals(1, fired.size());
        var briefing = fired.get(0).briefing();
        assertEquals(2, briefing.signals().size());
        assertEquals(4.0, briefing.signals().get(0).significance());
    }

    @Test
    void null_principal_broadcasts_to_all_registered_agents() {
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("a"), CognitiveDefaults.empty("b"));
        var acc = new CognitiveAttentionAccumulator(
            registry, null, fired::add, Clock.systemUTC(), 5.0, 0);

        acc.addSignals(List.of(
            new AttentionSignal(null, "t", SignalCategory.MERGE_CANDIDATE,
                "n1", "Dup entity", 6.0, "r")));
        assertEquals(2, fired.size());
    }

    @Test
    void threshold_floor_prevents_zero_threshold() {
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("a"));
        var acc = new CognitiveAttentionAccumulator(
            registry, null, fired::add, Clock.systemUTC(), 5.0, 0);

        acc.updateUrgencyP75("a", 1.0);
        // adjusted = 5.0 * (1.0 - 0.4) = 3.0 (floor = 5.0 * 0.6 = 3.0)
        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE,
                "n1", "G1", 2.5, "below floor")));
        assertTrue(fired.isEmpty(), "2.5 < 3.0 floor — should not fire");

        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.DECAY_DETECTED,
                "n2", "G2", 1.0, "crosses floor")));
        assertEquals(1, fired.size(), "3.5 >= 3.0 floor — should fire");
    }

    @Test
    void signals_sorted_by_significance_descending_in_briefing() {
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("a"));
        var acc = new CognitiveAttentionAccumulator(
            registry, null, fired::add, Clock.systemUTC(), 5.0, 0);

        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.DECAY_DETECTED,
                "n1", "Low", 1.0, "r"),
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE,
                "n2", "High", 3.0, "r"),
            new AttentionSignal("a", "t", SignalCategory.PRIORITY_SHIFT,
                "n3", "Mid", 2.0, "r")));
        assertEquals(1, fired.size());
        var signals = fired.get(0).briefing().signals();
        assertEquals(3.0, signals.get(0).significance());
        assertEquals(2.0, signals.get(1).significance());
        assertEquals(1.0, signals.get(2).significance());
    }

    @Test
    void pending_signals_cleared_after_fire() {
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("a"));
        var acc = new CognitiveAttentionAccumulator(
            registry, null, fired::add, Clock.systemUTC(), 5.0, 0);

        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE,
                "n1", "G1", 6.0, "r")));
        assertEquals(1, fired.size());

        // Below threshold now — pending was cleared
        acc.addSignals(List.of(
            new AttentionSignal("a", "t", SignalCategory.DECAY_DETECTED,
                "n2", "G2", 2.0, "r")));
        assertEquals(1, fired.size(), "below threshold after drain — no second fire");
    }
}
