package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttentionSignalTest {

    @Test
    void construction_preserves_all_fields() {
        var signal = new AttentionSignal(
            "agent-1", "tenant-1", SignalCategory.URGENCY_SPIKE,
            "node-42", "Project deadline", 0.85, "urgency rose to 0.85");
        assertEquals("agent-1", signal.principalId());
        assertEquals("tenant-1", signal.tenantId());
        assertEquals(SignalCategory.URGENCY_SPIKE, signal.category());
        assertEquals("node-42", signal.sourceNodeId());
        assertEquals("Project deadline", signal.sourceName());
        assertEquals(0.85, signal.significance());
        assertEquals("urgency rose to 0.85", signal.reason());
    }

    @Test
    void null_principalId_for_unresolvable_ownership() {
        var signal = new AttentionSignal(
            null, "tenant-1", SignalCategory.MERGE_CANDIDATE,
            "node-99", "Duplicate entity", 0.6, "Jaro-Winkler 0.91");
        assertNull(signal.principalId());
    }

    @Test
    void signal_category_covers_all_sources() {
        assertEquals(11, SignalCategory.values().length);
    }

    @Test
    void briefing_topN_returns_first_n_signals() {
        var signals = List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE, "n1", "G1", 0.9, "r1"),
            new AttentionSignal("a", "t", SignalCategory.DECAY_DETECTED, "n2", "G2", 0.5, "r2"),
            new AttentionSignal("a", "t", SignalCategory.GOAL_RECOGNIZED, "n3", "G3", 0.3, "r3"));
        var briefing = new AttentionBriefing("a", "t", signals, 0.7, Instant.now());
        assertEquals(2, briefing.topN(2).size());
        assertEquals(SignalCategory.URGENCY_SPIKE, briefing.topN(2).get(0).category());
    }

    @Test
    void briefing_topN_clamps_to_list_size() {
        var signals = List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE, "n1", "G1", 0.9, "r1"));
        var briefing = new AttentionBriefing("a", "t", signals, 0.5, Instant.now());
        assertEquals(1, briefing.topN(10).size());
    }

    @Test
    void briefing_signals_are_immutable() {
        var signals = new ArrayList<>(List.of(
            new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE, "n1", "G1", 0.9, "r1")));
        var briefing = new AttentionBriefing("a", "t", signals, 0.5, Instant.now());
        signals.clear();
        assertEquals(1, briefing.signals().size());
    }
}
