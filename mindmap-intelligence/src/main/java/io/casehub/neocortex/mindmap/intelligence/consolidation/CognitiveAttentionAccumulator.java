package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.CognitiveAttentionRequired;
import io.casehub.neocortex.mindmap.MindMapStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class CognitiveAttentionAccumulator {

    private final ConcurrentHashMap<String, PrincipalAttention> perPrincipal =
        new ConcurrentHashMap<>();
    private final CognitiveDefaultsRegistry registry;
    private final MindMapStore mindMapStore;
    private final Consumer<CognitiveAttentionRequired> eventSink;
    private final Clock clock;
    private final double baseThreshold;
    private final long minIntervalSeconds;

    static class PrincipalAttention {
        final ConcurrentLinkedQueue<AttentionSignal> pending = new ConcurrentLinkedQueue<>();
        volatile Instant lastPushAt = Instant.EPOCH;
        volatile double urgencyP75 = 0.0;
    }

    public CognitiveAttentionAccumulator(
            CognitiveDefaultsRegistry registry,
            MindMapStore mindMapStore,
            Consumer<CognitiveAttentionRequired> eventSink,
            Clock clock,
            double baseThreshold,
            long minIntervalSeconds) {
        this.registry = registry;
        this.mindMapStore = mindMapStore;
        this.eventSink = eventSink;
        this.clock = clock;
        this.baseThreshold = baseThreshold;
        this.minIntervalSeconds = minIntervalSeconds;
    }

    public void addSignals(List<AttentionSignal> signals) {
        var byPrincipal = new HashMap<String, List<AttentionSignal>>();
        for (var signal : signals) {
            String key = signal.principalId() != null
                ? signal.principalId() : "__broadcast__";
            byPrincipal.computeIfAbsent(key, k -> new ArrayList<>()).add(signal);
        }

        var broadcastSignals = byPrincipal.remove("__broadcast__");

        for (var entry : byPrincipal.entrySet()) {
            var pa = perPrincipal.computeIfAbsent(
                entry.getKey(), k -> new PrincipalAttention());
            deduplicateAndAdd(pa, entry.getValue());
            evaluateThreshold(entry.getKey(), pa);
        }

        if (broadcastSignals != null && registry != null) {
            for (String agentId : registry.allAgentIds()) {
                var pa = perPrincipal.computeIfAbsent(
                    agentId, k -> new PrincipalAttention());
                deduplicateAndAdd(pa, broadcastSignals);
                evaluateThreshold(agentId, pa);
            }
        }
    }

    public void updateUrgencyP75(String principalId, double urgencyP75) {
        var pa = perPrincipal.computeIfAbsent(
            principalId, k -> new PrincipalAttention());
        pa.urgencyP75 = Math.min(1.0, urgencyP75);
    }

    private void deduplicateAndAdd(
            PrincipalAttention pa, List<AttentionSignal> signals) {
        for (var signal : signals) {
            AttentionSignal existing = null;
            for (var s : pa.pending) {
                if (Objects.equals(s.sourceNodeId(), signal.sourceNodeId())
                        && s.category() == signal.category()) {
                    existing = s;
                    break;
                }
            }
            if (existing != null) {
                if (signal.significance() > existing.significance()) {
                    pa.pending.remove(existing);
                    pa.pending.add(signal);
                }
            } else {
                pa.pending.add(signal);
            }
        }
    }

    private void evaluateThreshold(String principalId, PrincipalAttention pa) {
        double urgencyP75 = Math.min(1.0, pa.urgencyP75);
        double adjusted = urgencyP75 > 0.6
            ? baseThreshold * (1.0 - (urgencyP75 - 0.6))
            : baseThreshold;
        adjusted = Math.max(adjusted, baseThreshold * 0.6);

        double total = 0;
        for (var s : pa.pending) {
            total += s.significance();
        }
        if (total < adjusted) return;

        Instant now = clock.instant();
        if (Duration.between(pa.lastPushAt, now).getSeconds() < minIntervalSeconds) {
            return;
        }

        var signalList = new ArrayList<>(pa.pending);
        signalList.sort(
            Comparator.comparingDouble(AttentionSignal::significance).reversed());
        pa.pending.clear();
        pa.lastPushAt = now;

        String tenantId = signalList.isEmpty() ? "" : signalList.get(0).tenantId();
        var briefing = new AttentionBriefing(
            principalId, tenantId, signalList, urgencyP75, now);
        eventSink.accept(new CognitiveAttentionRequired(briefing, now));
    }
}
