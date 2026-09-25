package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.mood.AffectRecorded;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.CognitiveAttentionRequired;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SignalCategory;

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
    private final ConcurrentHashMap<String, PadEntry> padCache =
        new ConcurrentHashMap<>();
    private final CognitiveDefaultsRegistry registry;
    private final MindMapStore mindMapStore;
    private final Consumer<CognitiveAttentionRequired> eventSink;
    private final Clock clock;
    private final double baseThreshold;
    private final long minIntervalSeconds;
    private final long padCacheExpirySeconds;

    record PadEntry(double[] pad, Instant updatedAt) {}

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
        this(registry, mindMapStore, eventSink, clock, baseThreshold, minIntervalSeconds, 600);
    }

    public CognitiveAttentionAccumulator(
            CognitiveDefaultsRegistry registry,
            MindMapStore mindMapStore,
            Consumer<CognitiveAttentionRequired> eventSink,
            Clock clock,
            double baseThreshold,
            long minIntervalSeconds,
            long padCacheExpirySeconds) {
        this.registry = registry;
        this.mindMapStore = mindMapStore;
        this.eventSink = eventSink;
        this.clock = clock;
        this.baseThreshold = baseThreshold;
        this.minIntervalSeconds = minIntervalSeconds;
        this.padCacheExpirySeconds = padCacheExpirySeconds;
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

    public void onAffectRecorded(AffectRecorded event) {
        if (mindMapStore == null) {return;}
        MindMapNode node = mindMapStore.getNode(event.nodeId(), event.tenantId());
        if (node == null) {return;}

        double   p       = node.pleasure() != null ? node.pleasure() : 0.0;
        double   a       = node.arousal() != null ? node.arousal() : 0.0;
        double   d       = node.dominance() != null ? node.dominance() : 0.0;
        double[] current = {p, a, d};
        Instant  now     = clock.instant();

        PadEntry previous = padCache.put(event.nodeId(), new PadEntry(current, now));
        if (previous == null) {return;}

        if (Duration.between(previous.updatedAt(), now).getSeconds() > padCacheExpirySeconds) {
            return;
        }

        double delta = Math.sqrt(
                Math.pow(current[0] - previous.pad()[0], 2)
                + Math.pow(current[1] - previous.pad()[1], 2)
                + Math.pow(current[2] - previous.pad()[2], 2));

        if (delta > 0.3) {
            String principalId = node.property("agent-id").orElse(null);
            addSignals(List.of(new AttentionSignal(
                    principalId, event.tenantId(), SignalCategory.AFFECT_CHANGE,
                    event.nodeId(), node.name(), delta,
                    "PAD delta " + String.format("%.2f", delta))));
        }
    }

    public void onExperienceRecorded(ExperienceRecorded event) {
        var metadata = event.event().metadata();
        if (metadata == null) {return;}
        String goalNodeId = metadata.get("goal-node-id");
        if (goalNodeId == null) {return;}

        String principalId = event.event().agentId();
        String tenantId    = event.event().tenantId();
        addSignals(List.of(new AttentionSignal(
                principalId, tenantId, SignalCategory.URGENCY_SPIKE,
                goalNodeId, event.event().description(), 1.0,
                "experience with goal context")));
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
