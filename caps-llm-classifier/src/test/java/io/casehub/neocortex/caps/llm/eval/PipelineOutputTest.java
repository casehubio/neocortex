package io.casehub.neocortex.caps.llm.eval;

import io.casehub.neocortex.caps.testing.InMemoryCapsEngine;
import io.casehub.neocortex.cognition.prompt.BehavioralPromptSection;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.SelfStandardDerivationPhase;
import io.casehub.neocortex.mindmap.intelligence.consolidation.PrioritySignatureDerivationPhase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds formation memories, runs consolidation phases, and prints what
 * BehavioralPromptSection produces. Validates the cognitive pipeline
 * produces personality signal from formation memories.
 *
 * <p>Replaces the CDI-based PipelineOutputTest from wacky-manor — uses
 * InMemoryMindMapStore and InMemoryCapsEngine instead of CDI-injected
 * persistent stores.
 */
@Tag("llm-eval")
class PipelineOutputTest {

    private static final String TENANT = "pipeline-output-test";

    @Test
    void seedAndConsolidate_showsPipelineOutput() {
        var characters = EvalCharacterLoader.loadGeneric();
        var store = new InMemoryMindMapStore();
        var capsEngine = new InMemoryCapsEngine();

        for (var entry : characters.entrySet()) {
            String agentId = entry.getKey();
            EvalCharacterData data = entry.getValue();

            String sgId = EvalMindMapSeeder.seedCharacter(agentId, data, store, TENANT);
            System.out.printf("[%s] Seeded %d drives, %d formation memories%n",
                    agentId, data.drives().size(), data.formationMemories().size());

            capsEngine.initializeAgent(TENANT, agentId,
                    new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative"));

            seedGraduatedMemories(agentId, data, store, sgId);
        }

        System.out.println("\n=== MINDMAP SUBGRAPHS (before consolidation) ===");
        printSubgraphs(store);

        System.out.println("\n=== RUNNING CONSOLIDATION PHASES ===");
        new SelfStandardDerivationPhase(store).run(TENANT, List.of());
        new PrioritySignatureDerivationPhase(store).run(TENANT, List.of());
        System.out.println("=== DONE ===");

        seedBehavioralAttractors(store);

        System.out.println("\n=== MINDMAP SUBGRAPHS (after consolidation) ===");
        printSubgraphs(store);

        var behavioral = new BehavioralPromptSection(store);
        for (String agentId : new String[]{"hooded-claw", "peter-perfect"}) {
            var ctx = new CognitionRenderContext(agentId, TENANT, null);
            var output = behavioral.render(ctx);
            System.out.printf("\n=== %s: BehavioralPromptSection ===%n", agentId);
            System.out.println(output != null ? output : "(no output — no attractors)");
        }
    }

    private void seedGraduatedMemories(String agentId, EvalCharacterData data,
                                        InMemoryMindMapStore store, String sgId) {
        int idx = 0;
        for (var mem : data.formationMemories()) {
            store.addNode(NodeInput.of("graduated-" + agentId + "-" + idx, sgId)
                    .withTraits(Set.of("GraduatedMemory"))
                    .withProperties(Map.of(
                            "agent-id", agentId,
                            "source-memory-id", "mem-" + agentId + "-" + idx,
                            "text", mem.episode().substring(0, Math.min(100, mem.episode().length())))), TENANT);
            idx++;
        }
    }

    private void seedBehavioralAttractors(InMemoryMindMapStore store) {
        var sgId = store.createSubgraph(
                new SubgraphInput("behavioral-attractors", SubgraphTypes.BEHAVIORAL, null), TENANT);

        store.addNode(NodeInput.of("attractor-hc-scheming", sgId)
                .withTraits(Set.of("CapsGenerated"))
                .withProperties(Map.of(
                        "agent-id", "hooded-claw",
                        "caps-node-id", "scheming",
                        "personality-label", "tendency toward elaborate planning",
                        "strength", "0.75",
                        "category", "scheming",
                        "source-names", "Years of calculated patience")), TENANT);

        store.addNode(NodeInput.of("attractor-pp-gallantry", sgId)
                .withTraits(Set.of("CapsGenerated"))
                .withProperties(Map.of(
                        "agent-id", "peter-perfect",
                        "caps-node-id", "gallantry",
                        "personality-label", "instinct to help with grace",
                        "strength", "0.80",
                        "category", "gallantry",
                        "source-names", "A lifetime of stepping in")), TENANT);

        store.addNode(NodeInput.of("attractor-pp-protection", sgId)
                .withTraits(Set.of("CapsGenerated"))
                .withProperties(Map.of(
                        "agent-id", "peter-perfect",
                        "caps-node-id", "protection",
                        "personality-label", "vigilance for those in your care",
                        "strength", "0.65",
                        "category", "protection",
                        "source-names", "Watching your father help a stranger")), TENANT);
    }

    private void printSubgraphs(InMemoryMindMapStore store) {
        for (var sg : store.listSubgraphs(TENANT)) {
            var nodes = store.nodesIn(sg.id(), TENANT);
            System.out.printf("  '%s' (%s): %d nodes%n", sg.name(), sg.type(), nodes.size());
            if ("behavioral".equals(sg.type()) || "cognitive".equals(sg.type())) {
                for (var n : nodes.stream().limit(5).toList()) {
                    System.out.printf("    %s traits=%s agent=%s%n", n.name(), n.traits(),
                            n.property("agent-id").orElse("?"));
                }
            }
        }
    }
}
