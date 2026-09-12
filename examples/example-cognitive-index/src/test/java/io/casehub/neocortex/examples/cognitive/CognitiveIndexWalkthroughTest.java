package io.casehub.neocortex.examples.cognitive;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.index.AffectSnapshot;
import io.casehub.neocortex.cognitive.index.AgentPair;
import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfileQuery;
import io.casehub.neocortex.cognitive.index.CorrelationStrength;
import io.casehub.neocortex.cognitive.index.DomainActivation;
import io.casehub.neocortex.cognitive.index.DomainActivationQuery;
import io.casehub.neocortex.cognitive.index.DomainActivationResult;
import io.casehub.neocortex.cognitive.index.DomainCorrelation;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.cognitive.index.PadDimension;
import io.casehub.neocortex.cognitive.index.PerspectivalComparison;
import io.casehub.neocortex.cognitive.index.SocialComparison;
import io.casehub.neocortex.cognitive.index.TrendAgreement;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Walkthrough: multi-agent social cognition in a therapy setting.
 *
 * Scenario — a family therapy session. Therapist Dr. Chen, patient Maya,
 * and family member Rosa each perceive "Grandmother" differently:
 *
 *   Dr. Chen sees a stable anchor figure (clinical assessment)
 *   Maya sees someone she deeply loves but worries about (emotional attachment)
 *   Rosa sees tension and declining relationship (strained family dynamics)
 *
 * Each test method demonstrates a cognitive-index capability:
 *   1. Build the world — entities, relationships, affect memories
 *   2. Single-entity resolve — unified EntityKnowledge from MindMap + Memory
 *   3. Perspectival resolve — same entity through one agent's lens
 *   4. Multi-agent compare — N agents, one entity, N perspectives
 *   5. Social comparison — divergence metrics across perspectives
 *   6. Cross-domain correlation — work stress ↔ family tension via DTW
 *   7. Capabilities matrix — summary of what was demonstrated
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CognitiveIndexWalkthroughTest {

    private static final String TENANT = "therapy-session";
    private static final Instant BASE = Instant.parse("2026-06-01T09:00:00Z");
    private static final Confidence CONF = new Confidence(ConfidenceOrigin.STATED, 0.9, BASE);
    private static final MemoryDomain AFFECT = new MemoryDomain("affect");

    private MindMapStore mindMapStore;
    private CaseMemoryStore memoryStore;
    private CognitiveProfile profile;
    private DomainActivation domainActivation;

    private final PrincipalId drChen = PrincipalId.agent("dr-chen");
    private final PrincipalId maya = PrincipalId.agent("maya");
    private final PrincipalId rosa = PrincipalId.agent("rosa");

    private String familySgId;
    private String workSgId;
    private String grandmaId;
    private String mayaNodeId;
    private String workplaceId;

    @BeforeAll
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new SimpleMemoryStore();
        profile = new CognitiveProfile(mindMapStore, memoryStore);
        domainActivation = new DomainActivation(mindMapStore, memoryStore);
    }

    // ── Phase 1: Build the World ─────────────────────────────────────
    //
    // Create two life domains (family and work) with entities and
    // relationships. Store affect memories to establish emotional
    // baselines. Create perspectival overlays — each agent's private
    // emotional assessment of Grandmother.

    @Test @Order(1)
    void phase1_buildTheWorld() {
        familySgId = mindMapStore.createSubgraph(
            new SubgraphInput("Family", SubgraphTypes.PERSON, null), TENANT);
        workSgId = mindMapStore.createSubgraph(
            new SubgraphInput("Work", SubgraphTypes.ORGANISATION, null), TENANT);

        grandmaId = mindMapStore.addNode(
            NodeInput.of("Grandmother", familySgId)
                .withConfidence(CONF)
                .withProperties(Map.of("role", "matriarch", "age", "78")),
            TENANT);

        mayaNodeId = mindMapStore.addNode(
            NodeInput.of("Maya", familySgId)
                .withConfidence(CONF)
                .withProperties(Map.of("role", "granddaughter", "age", "34")),
            TENANT);

        workplaceId = mindMapStore.addNode(
            NodeInput.of("City Hospital", workSgId)
                .withConfidence(CONF)
                .withProperties(Map.of("industry", "healthcare")),
            TENANT);

        mindMapStore.addEdge(EdgeInput.of(mayaNodeId, grandmaId, "grandchild-of"), TENANT);
        mindMapStore.addEdge(EdgeInput.of(mayaNodeId, workplaceId, "works-at"), TENANT);

        // --- Perspectival overlays ---
        // Each agent sees Grandmother through a different emotional lens.
        // Overlays store private PAD assessments that merge onto the shared node.

        // Dr. Chen: positive clinical assessment — calm, confident, warm
        addOverlay(grandmaId, drChen, 0.7, 0.2, 0.6);

        // Maya: deep love mixed with worry — high pleasure, high arousal
        addOverlay(grandmaId, maya, 0.8, 0.7, 0.3);

        // Rosa: strained relationship — low pleasure, moderate tension
        addOverlay(grandmaId, rosa, -0.1, 0.5, 0.2);

        // --- Affect memories ---
        // Store affect observations over time for trajectory computation.
        // Each memory is scoped to the observing agent via principalId.
        for (int day = 0; day < 8; day++) {
            Instant t = BASE.plus(Duration.ofDays(day));
            // Maya's affect toward Grandmother: gradually improving
            storeAffect(grandmaId, maya, 0.5 + 0.04 * day, 0.6, 0.3, t);
            // Rosa's affect toward Grandmother: gradually worsening
            storeAffect(grandmaId, rosa, 0.1 - 0.03 * day, 0.5, 0.2, t);
            // Dr. Chen: stable clinical assessment
            storeAffect(grandmaId, drChen, 0.7, 0.2, 0.6, t);

            // Maya's affect toward work — correlated stress with family worry
            storeAffect(workplaceId, maya, 0.3 + 0.03 * day, 0.5 + 0.02 * day, 0.4, t);
        }

        System.out.println("Phase 1: Built therapy world");
        System.out.println("  Family domain: Grandmother (matriarch, 78), Maya (granddaughter, 34)");
        System.out.println("  Work domain: City Hospital");
        System.out.println("  3 perspectival overlays on Grandmother (Dr. Chen, Maya, Rosa)");
        System.out.println("  " + (8 * 4) + " affect memories across agents and domains");
    }

    // ── Phase 2: Single-Entity Resolve ───────────────────────────────
    //
    // CognitiveProfile.resolve() pulls together everything known about
    // an entity: the MindMap node, its edges, memories across all
    // domains, and the computed affect trajectory — in one call.

    @Test @Order(2)
    void phase2_singleEntityResolve() {
        var query = CognitiveProfileQuery.byId(grandmaId, TENANT);
        Optional<EntityKnowledge> ek = profile.resolve(query);

        assertThat(ek).isPresent();
        EntityKnowledge knowledge = ek.get();

        // The node carries the shared (un-overlaid) view
        assertThat(knowledge.node().name()).isEqualTo("Grandmother");
        assertThat(knowledge.node().property("role")).contains("matriarch");

        // Edges connect Grandmother to the broader graph
        assertThat(knowledge.edges()).isNotEmpty();

        // Memories from all agents are returned (no principal filter)
        assertThat(knowledge.memories()).isNotEmpty();

        // No perceiver — this is the shared view
        assertThat(knowledge.perceiver()).isNull();

        System.out.println("Phase 2: Resolved Grandmother — shared view");
        System.out.println("  Node: " + knowledge.node().name()
            + " (type=" + knowledge.node().type() + ")");
        System.out.println("  Edges: " + knowledge.edges().size());
        System.out.println("  Memory domains: " + knowledge.memories().keySet());
        System.out.println("  Trajectory: " + knowledge.trajectory());
        System.out.println("  Perceiver: " + knowledge.perceiver() + " (shared view)");
    }

    // ── Phase 3: Perspectival Resolve ────────────────────────────────
    //
    // The same entity through one agent's lens. withAsSeenBy() applies
    // the agent's overlay BEFORE computing trajectory — perspective
    // is constitutive of the resolution, not a post-processing step.

    @Test @Order(3)
    void phase3_perspectivalResolve() {
        // Maya's view of Grandmother
        var query = CognitiveProfileQuery.byId(grandmaId, TENANT)
            .withAsSeenBy(maya);
        Optional<EntityKnowledge> ek = profile.resolve(query);

        assertThat(ek).isPresent();
        EntityKnowledge mayaView = ek.get();

        // Maya's overlay PAD values replace the shared node's
        assertThat(mayaView.node().pleasure()).isEqualTo(0.8);
        assertThat(mayaView.node().arousal()).isEqualTo(0.7);
        assertThat(mayaView.perceiver()).isEqualTo(maya);

        // Compare with Rosa's view
        var rosaQuery = CognitiveProfileQuery.byId(grandmaId, TENANT)
            .withAsSeenBy(rosa);
        EntityKnowledge rosaView = profile.resolve(rosaQuery).orElseThrow();

        assertThat(rosaView.node().pleasure()).isEqualTo(-0.1);
        assertThat(rosaView.perceiver()).isEqualTo(rosa);

        System.out.println("Phase 3: Perspectival resolve — same entity, different lenses");
        System.out.printf("  Maya sees Grandmother:  pleasure=%.1f  arousal=%.1f  dominance=%.1f%n",
            mayaView.node().pleasure(), mayaView.node().arousal(), mayaView.node().dominance());
        System.out.printf("  Rosa sees Grandmother:  pleasure=%.1f  arousal=%.1f  dominance=%.1f%n",
            rosaView.node().pleasure(), rosaView.node().arousal(), rosaView.node().dominance());
        System.out.println("  Same entity, radically different emotional assessments.");
    }

    // ── Phase 4: Multi-Agent Compare ─────────────────────────────────
    //
    // compare() resolves perspectives for all agents in a single call.
    // Internally, it loads ALL overlay nodes once (one store query),
    // partitions by agent, and resolves each — efficient even for
    // dozens of agents.

    @Test @Order(4)
    void phase4_multiAgentCompare() {
        var query = CognitiveProfileQuery.byId(grandmaId, TENANT);
        Map<PrincipalId, EntityKnowledge> perspectives =
            profile.compare(query, Set.of(drChen, maya, rosa));

        assertThat(perspectives).hasSize(3);

        // Each agent gets their own perspectival EntityKnowledge
        assertThat(perspectives.get(drChen).perceiver()).isEqualTo(drChen);
        assertThat(perspectives.get(maya).perceiver()).isEqualTo(maya);
        assertThat(perspectives.get(rosa).perceiver()).isEqualTo(rosa);

        // Each has their own overlaid PAD
        assertThat(perspectives.get(drChen).node().pleasure()).isEqualTo(0.7);
        assertThat(perspectives.get(maya).node().pleasure()).isEqualTo(0.8);
        assertThat(perspectives.get(rosa).node().pleasure()).isEqualTo(-0.1);

        System.out.println("Phase 4: Multi-agent compare — 3 agents, 1 entity, 3 perspectives");
        for (var entry : perspectives.entrySet()) {
            EntityKnowledge ek = entry.getValue();
            System.out.printf("  %s: pleasure=%.1f  arousal=%.1f  dominance=%.1f%n",
                entry.getKey().value(),
                ek.node().pleasure(), ek.node().arousal(), ek.node().dominance());
        }
    }

    // ── Phase 5: Social Comparison ───────────────────────────────────
    //
    // SocialComparison takes the multi-agent perspectives and computes
    // divergence metrics: PAD distances, per-dimension differences,
    // and trajectory alignment. This is where "how differently do
    // agents feel about the same entity?" gets quantified.

    @Test @Order(5)
    void phase5_socialComparison() {
        Map<PrincipalId, EntityKnowledge> perspectives =
            profile.compare(CognitiveProfileQuery.byId(grandmaId, TENANT),
                Set.of(drChen, maya, rosa));

        PerspectivalComparison comparison = SocialComparison.compare(perspectives);

        assertThat(comparison.agentCount()).isEqualTo(3);
        assertThat(comparison.entityName()).isEqualTo("Grandmother");

        // PAD distance matrix — who feels most differently?
        double mayaRosaDist = comparison.distances().distance(maya, rosa);
        double chenMayaDist = comparison.distances().distance(drChen, maya);
        double chenRosaDist = comparison.distances().distance(drChen, rosa);

        // Rosa and Maya should be most divergent (opposite pleasure)
        assertThat(mayaRosaDist).isGreaterThan(chenMayaDist);

        // Per-dimension signed differences — who feels more pleasure?
        double pleasureDiff = comparison.dimensionDifferences()
            .get(PadDimension.PLEASURE).difference(maya, rosa);
        assertThat(pleasureDiff).isGreaterThan(0.5);

        // Trajectory alignment — are agents trending the same direction?
        AgentPair mayaRosa = AgentPair.of(maya, rosa);
        TrendAgreement agreement = comparison.trajectoryAlignment()
            .agreements().get(mayaRosa);
        assertThat(agreement).isEqualTo(TrendAgreement.DIVERGENT);

        System.out.println("Phase 5: Social comparison — divergence metrics");
        System.out.println("  PAD distances:");
        System.out.printf("    Maya ↔ Rosa:     %.3f  (most divergent)%n", mayaRosaDist);
        System.out.printf("    Dr. Chen ↔ Maya: %.3f%n", chenMayaDist);
        System.out.printf("    Dr. Chen ↔ Rosa: %.3f%n", chenRosaDist);
        System.out.printf("  Pleasure: Maya is %.2f higher than Rosa%n", pleasureDiff);
        System.out.println("  Trajectory: Maya ↔ Rosa = " + agreement
            + " (Maya improving, Rosa worsening)");
        System.out.println("  Per-agent snapshots:");
        for (var entry : comparison.perspectives().entrySet()) {
            AffectSnapshot snap = entry.getValue();
            System.out.printf("    %s: P=%.1f  A=%.1f  D=%.1f  trend=%s%n",
                entry.getKey().value(),
                snap.pleasure(), snap.arousal(), snap.dominance(),
                snap.trajectory() != null ? snap.trajectory().trend() : "n/a");
        }
    }

    // ── Phase 6: Cross-Domain Correlation ────────────────────────────
    //
    // DomainActivation correlates affect signals across life domains.
    // "Does Maya's work stress track with her family worry?" — answered
    // via DTW on time-bucketed PAD series.

    @Test @Order(6)
    void phase6_crossDomainCorrelation() {
        var query = DomainActivationQuery
            .between(maya, TENANT, familySgId, workSgId)
            .withFrom(BASE)
            .withTo(BASE.plus(Duration.ofDays(8)))
            .withBucketDuration(Duration.ofDays(1));

        Optional<DomainActivationResult> result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        DomainActivationResult correlation = result.get();

        assertThat(correlation.domains()).hasSize(2);
        assertThat(correlation.correlations()).hasSize(1);

        DomainCorrelation corr = correlation.correlations().values().iterator().next();
        assertThat(corr.dtwSimilarity()).isGreaterThan(0.0);
        assertThat(corr.strength()).isNotNull();

        System.out.println("Phase 6: Cross-domain correlation — Maya's family ↔ work affect");
        System.out.printf("  DTW similarity: %.3f (%s)%n",
            corr.dtwSimilarity(), corr.strength());
        System.out.println("  Alignment path: " + corr.alignment().size() + " time points matched");
        System.out.println("  Family domain: " + correlation.domains().get(familySgId).memoryCount()
            + " memories, " + correlation.domains().get(familySgId).entityCount() + " entities");
        System.out.println("  Work domain: " + correlation.domains().get(workSgId).memoryCount()
            + " memories, " + correlation.domains().get(workSgId).entityCount() + " entities");
        System.out.println("  Interpretation: Maya's improving family affect correlates with");
        System.out.println("  her improving work affect — emotional domains are linked.");
    }

    // ── Phase 7: Capabilities Matrix ─────────────────────────────────

    @Test @Order(7)
    void phase7_capabilitiesMatrix() {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Cognitive Index — Capabilities Matrix");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("  %-42s %-8s %s%n", "Capability", "Phase", "API");
        System.out.println("  " + "─".repeat(78));
        System.out.printf("  %-42s %-8s %s%n", "Cross-store entity resolution", "2", "CognitiveProfile.resolve()");
        System.out.printf("  %-42s %-8s %s%n", "Perspectival overlay merging", "3", "resolve() + withAsSeenBy()");
        System.out.printf("  %-42s %-8s %s%n", "Principal-scoped memory queries", "3", "withCallerPrincipalId()");
        System.out.printf("  %-42s %-8s %s%n", "Multi-agent batched comparison", "4", "CognitiveProfile.compare()");
        System.out.printf("  %-42s %-8s %s%n", "Single-scan overlay loading", "4", "loadAllOverlays() internally");
        System.out.printf("  %-42s %-8s %s%n", "PAD distance matrix", "5", "SocialComparison.compare()");
        System.out.printf("  %-42s %-8s %s%n", "Per-dimension signed differences", "5", "PairwiseDifferences.difference()");
        System.out.printf("  %-42s %-8s %s%n", "3D trajectory alignment (cosine)", "5", "TrajectoryAlignment");
        System.out.printf("  %-42s %-8s %s%n", "Cross-domain DTW correlation", "6", "DomainActivation.correlate()");
        System.out.printf("  %-42s %-8s %s%n", "Time-bucketed PAD aggregation", "6", "3D PAD per bucket");
        System.out.printf("  %-42s %-8s %s%n", "Privacy by construction", "6", "PrincipalId required, non-nullable");
        System.out.println();
        System.out.println("  Key insight: perspective is constitutive — applied BEFORE trajectory,");
        System.out.println("  not after. Each agent sees a different entity, not the same entity");
        System.out.println("  with a different label.");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    // --- helpers ---

    private void addOverlay(String sharedNodeId, PrincipalId agent,
                            double pleasure, double arousal, double dominance) {
        mindMapStore.addNode(
            NodeInput.of("overlay-" + agent.value(), familySgId)
                .withConfidence(CONF)
                .withTraits(Set.of("overlay"))
                .withRefs(Set.of(OverlayRef.of(sharedNodeId)))
                .withPleasure(pleasure).withArousal(arousal).withDominance(dominance)
                .withProperties(Map.of(OverlayRef.AGENT_ID, agent.value())),
            TENANT);
    }

    private void storeAffect(String entityId, PrincipalId agent,
                             double pleasure, double arousal, double dominance,
                             Instant timestamp) {
        memoryStore.store(
            MemoryInput.ownedBy(
                    Subject.of("unknown", entityId), AFFECT, TENANT,
                    "affect observation at " + timestamp, agent)
                .withPad(pleasure, arousal, dominance)
                .withConfidence(CONF));
    }
}
