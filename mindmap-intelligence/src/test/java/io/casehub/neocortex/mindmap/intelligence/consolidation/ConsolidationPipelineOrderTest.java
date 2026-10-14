package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.runtime.IdleTracker;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolidationPipelineOrderTest {

    enum TestPrincipal implements CurrentPrincipal {
        INSTANCE;
        @Override public String actorId() { return "test"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return "pipeline-test"; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    }

    @jakarta.annotation.Priority(10)
    static class EarlyPhase implements ConsolidationPhase {
        final List<String> log;
        EarlyPhase(List<String> log) { this.log = log; }
        @Override public String name() { return "early"; }
        @Override public void run(String t, List<String> sp) { log.add("early"); }
    }

    @jakarta.annotation.Priority(30)
    static class LatePhase implements ConsolidationPhase {
        final List<String> log;
        LatePhase(List<String> log) { this.log = log; }
        @Override public String name() { return "late"; }
        @Override public void run(String t, List<String> sp) { log.add("late"); }
    }

    @Test
    void prioritySortWalksSuperclassChain() {
        var log = new ArrayList<String>();
        List<ConsolidationPhase> phases = List.of(new LatePhase(log), new EarlyPhase(log));

        var sorted = phases.stream()
            .sorted(Comparator.comparingInt(p -> {
                for (Class<?> c = p.getClass(); c != null; c = c.getSuperclass()) {
                    var ann = c.getAnnotation(jakarta.annotation.Priority.class);
                    if (ann != null) return ann.value();
                }
                return Integer.MAX_VALUE;
            }))
            .toList();

        sorted.forEach(p -> p.run("t1", List.of()));
        assertThat(log).containsExactly("early", "late");
    }

    @Test
    void downstreamPhaseSeesUpstreamOutput() {
        var store = new InMemoryMindMapStore();
        var tenant = "pipeline-test";

        var sgId = store.createSubgraph(
            new SubgraphInput("cognitive-test", SubgraphTypes.COGNITIVE, null), tenant);

        var downstreamInput = new ArrayList<String>();

        ConsolidationPhase upstream = new ConsolidationPhase() {
            @Override public String name() { return "upstream"; }
            @Override public void run(String tenantId, List<String> sp) {
                store.addNode(NodeInput.of("graduated-memory", sgId)
                    .withProperties(Map.of("cognitiveKind", "formative-experience",
                        "agent-id", "test-agent")), tenantId);
            }
        };

        ConsolidationPhase downstream = new ConsolidationPhase() {
            @Override public String name() { return "downstream"; }
            @Override public void run(String tenantId, List<String> sp) {
                var nodes = store.search(
                    MindMapQuery.of(tenantId, 100).withType(SubgraphTypes.COGNITIVE));
                long formativeCount = nodes.stream()
                    .filter(n -> "formative-experience".equals(
                        n.properties().get("cognitiveKind")))
                    .count();
                downstreamInput.add("found:" + formativeCount);
            }
        };

        // upstream runs first, then downstream — simulates correct priority ordering
        upstream.run(tenant, List.of());
        downstream.run(tenant, List.of());

        assertThat(downstreamInput).containsExactly("found:1");
    }
}
