package io.casehub.neocortex.examples.goal;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.ModulationProfile;
import io.casehub.neocortex.cognitive.index.GoalRelevanceModulationFactor;
import io.casehub.neocortex.cognitive.index.ModulationProfiles;
import io.casehub.neocortex.mindmap.CognitiveGoalDecomposer;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.GoalDecompositionResult;
import io.casehub.neocortex.mindmap.GoalVocabulary;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.intelligence.Goallike;
import io.casehub.neocortex.mindmap.intelligence.consolidation.GoalAffectPhase;
import io.casehub.neocortex.mindmap.intelligence.consolidation.GoalPrioritizationPhase;
import io.casehub.neocortex.mindmap.intelligence.consolidation.GoalRecognitionPhase;
import io.casehub.neocortex.mindmap.intelligence.consolidation.GoalResolutionPhase;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walkthrough: cognitive goal management across four domains.
 *
 * Four scenarios demonstrate different aspects of goal cognition:
 * a research agent managing paper deadlines (progressive resolution),
 * a product manager tracking feature dependencies (dependency revision),
 * a personal assistant discovering implicit goals (recognition + affect),
 * and a game NPC with overlapping quests (merge + retrieval modulation).
 *
 * Each test method is a phase:
 *   1. Build the goal graph — goals with varied horizons and dependencies
 *   2. Goallike trait access — typed property access via Thing.as()
 *   3. Goal vocabulary — typed edges with alias resolution
 *   4. Expand — approaching goals decompose into sub-goals
 *   5. Prune — distant goals collapse back to low resolution
 *   6. Merge — shared sub-goals across parents
 *   7. Revise — blocker completion unblocks dependent goals
 *   8. Affect — PAD emotional computation from goal status
 *   9. Priority — composite formula ranks competing goals
 *  10. Retrieval modulation — memories biased by goal proximity
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GoalCognitionWalkthroughTest {

    private InMemoryMindMapStore store;
    private InMemoryMemoryStore memoryStore;
    private static final String TENANT = "walkthrough";
    private static final ModulationProfile<Memory> MEMORY_PROFILE = ModulationProfiles.MEMORY;

    private String goalSgId;
    private String conceptSgId;

    // Research agent goals
    private String understandTransformersId;
    private String submitPaperId;

    // Product team goals
    private String shipV2Id;
    private String hireSeniorId;

    // Personal assistant goal
    private String learnToCookId;

    // Game NPC goals
    private String rescuePrincessId;
    private String findArtifactId;

    @BeforeAll
    void setUp() {
        store = new InMemoryMindMapStore();
        store.registerVocabulary(GoalVocabulary.GOAL_VOCABULARY);
        CurrentPrincipal principal = new CurrentPrincipal() {
            @Override public String actorId() { return "actor"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return true; }
        };
        memoryStore = new InMemoryMemoryStore(principal);
        goalSgId = store.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
        conceptSgId = store.createSubgraph(
                new SubgraphInput("Concepts", SubgraphTypes.CONCEPT, null), TENANT);
    }

    // ── Phase 1: Build the goal graph ────────────────────────────────

    @Test @Order(1)
    void phase1_buildGoalGraph() {
        // Research agent — aspirational vs imminent
        understandTransformersId = store.addNode(NodeInput.of("Understand transformers", goalSgId)
                .withProperties(Map.of(
                        "description", "Develop deep understanding of transformer architectures",
                        "status", "active",
                        "horizon", "aspirational",
                        "resolution", "low",
                        "urgency", "0.2",
                        "feasibility", "0.4",
                        "origin", "reflection")), TENANT);

        submitPaperId = store.addNode(NodeInput.of("Submit NeurIPS paper", goalSgId)
                .withProperties(Map.of(
                        "description", "Submit attention mechanisms survey to NeurIPS",
                        "status", "active",
                        "horizon", "immediate",
                        "resolution", "low",
                        "urgency", "0.9",
                        "feasibility", "0.7",
                        "origin", "explicit")), TENANT);

        store.addEdge(EdgeInput.of(submitPaperId, understandTransformersId, "requires"), TENANT);

        // Product team — blocked dependency
        shipV2Id = store.addNode(NodeInput.of("Ship v2.0", goalSgId)
                .withProperties(Map.of(
                        "description", "Ship product version 2.0",
                        "status", "blocked",
                        "horizon", "short",
                        "urgency", "0.8",
                        "feasibility", "0.5")), TENANT);

        hireSeniorId = store.addNode(NodeInput.of("Hire senior engineer", goalSgId)
                .withProperties(Map.of(
                        "description", "Hire a senior backend engineer",
                        "status", "active",
                        "horizon", "medium",
                        "urgency", "0.6",
                        "feasibility", "0.8")), TENANT);

        store.addEdge(EdgeInput.of(hireSeniorId, shipV2Id, "blocks"), TENANT);

        // Personal assistant — discovered from experience
        learnToCookId = store.addNode(NodeInput.of("Learn to cook", goalSgId)
                .withProperties(Map.of(
                        "description", "Learn to cook healthy meals",
                        "status", "active",
                        "horizon", "medium",
                        "urgency", "0.4",
                        "feasibility", "0.9",
                        "origin", "experience")), TENANT);

        assertThat(store.nodesIn(goalSgId, TENANT)).hasSize(5);
    }

    // ── Phase 2: Goallike trait access ───────────────────────────────

    @Test @Order(2)
    void phase2_goallikeTraitAccess() {
        MindMapNode paperGoal = store.getNode(submitPaperId, TENANT);

        Goallike goal = paperGoal.as(Goallike.class);
        assertThat(goal.description()).contains("Submit attention mechanisms survey to NeurIPS");
        assertThat(goal.status()).contains("active");
        assertThat(goal.horizon()).contains("immediate");
        assertThat(goal.origin()).contains("explicit");
        assertThat(goal.resolution()).contains("low");
        assertThat(goal.urgency()).contains("0.9");
        assertThat(goal.feasibility()).contains("0.7");
    }

    // ── Phase 3: Goal vocabulary and edges ───────────────────────────

    @Test @Order(3)
    void phase3_goalVocabularyAndEdges() {
        assertThat(GoalVocabulary.GOAL_VOCABULARY.edgeTypes()).hasSize(5);

        // Alias resolution: "depends-on" resolves to "requires"
        String improveOnboardingId = store.addNode(NodeInput.of("Improve onboarding", goalSgId)
                .withProperties(Map.of("description", "Improve onboarding flow",
                        "status", "active")), TENANT);
        String reduceChurnId = store.addNode(NodeInput.of("Reduce churn", goalSgId)
                .withProperties(Map.of("description", "Reduce customer churn",
                        "status", "active")), TENANT);

        store.addEdge(EdgeInput.of(improveOnboardingId, reduceChurnId, "helps"), TENANT);

        List<MindMapEdge> edges = store.neighbors(improveOnboardingId, "contributes-to", TENANT);
        assertThat(edges).isNotEmpty();
    }

    // ── Phase 4: Expand approaching goals ────────────────────────────

    @Test @Order(4)
    void phase4_expandApproachingGoal() {
        CognitiveGoalDecomposer decomposer = (desc, ctx, tid) -> {
            if (desc.contains("Submit attention")) {
                return new GoalDecompositionResult(
                        List.of(
                                new GoalDecompositionResult.SubGoal("Literature review", "immediate", Map.of()),
                                new GoalDecompositionResult.SubGoal("Run experiments", "immediate", Map.of()),
                                new GoalDecompositionResult.SubGoal("Write paper draft", "immediate", Map.of())),
                        List.of(
                                new GoalDecompositionResult.GoalRelationship(desc, "Literature review", "decomposes-into"),
                                new GoalDecompositionResult.GoalRelationship(desc, "Run experiments", "decomposes-into"),
                                new GoalDecompositionResult.GoalRelationship(desc, "Write paper draft", "decomposes-into")));
            }
            return GoalDecompositionResult.EMPTY;
        };

        new GoalResolutionPhase(store, decomposer, (a, t) -> Map.of())
                .run(TENANT, List.of());

        MindMapNode paper = store.getNode(submitPaperId, TENANT);
        assertThat(paper.property("resolution")).contains("high");

        List<MindMapEdge> subGoalEdges = store.neighbors(submitPaperId, "decomposes-into", TENANT)
                .stream()
                .filter(e -> e.sourceNodeId().equals(submitPaperId))
                .toList();
        assertThat(subGoalEdges).hasSize(3);
    }

    // ── Phase 5: Prune distant goals ─────────────────────────────────

    @Test @Order(5)
    void phase5_pruneDistantGoal() {
        // Give the aspirational goal fake sub-goals to prune
        store.updateNode(understandTransformersId,
                NodeUpdate.empty().withPropertiesToSet(Map.of("resolution", "high")),
                TENANT);
        String sub = store.addNode(NodeInput.of("Read Vaswani 2017", goalSgId)
                .withProperties(Map.of("description", "Read attention paper", "status", "active")), TENANT);
        store.addEdge(EdgeInput.of(understandTransformersId, sub, "decomposes-into"), TENANT);

        new GoalResolutionPhase(store, (d, c, t) -> GoalDecompositionResult.EMPTY,
                (a, t) -> Map.of()).run(TENANT, List.of());

        MindMapNode aspirational = store.getNode(understandTransformersId, TENANT);
        assertThat(aspirational.property("resolution")).contains("low");
        assertThat(store.getNode(sub, TENANT)).isNull();
    }

    // ── Phase 6: Merge shared sub-goals ──────────────────────────────

    @Test @Order(6)
    void phase6_mergeSharedSubGoals() {
        rescuePrincessId = store.addNode(NodeInput.of("Rescue the princess", goalSgId)
                .withProperties(Map.of("description", "Rescue Princess Elara from the tower",
                        "status", "active")), TENANT);
        findArtifactId = store.addNode(NodeInput.of("Find the ancient artifact", goalSgId)
                .withProperties(Map.of("description", "Locate the Crystal of Eternity",
                        "status", "active")), TENANT);

        String talkBlacksmith1 = store.addNode(NodeInput.of("find the blacksmith shop", goalSgId)
                .withProperties(Map.of("description", "find the blacksmith shop for weapons",
                        "status", "active")), TENANT);
        String visitBlacksmith2 = store.addNode(NodeInput.of("find the blacksmith store", goalSgId)
                .withProperties(Map.of("description", "find the blacksmith store for gear",
                        "status", "active")), TENANT);

        store.addEdge(EdgeInput.of(rescuePrincessId, talkBlacksmith1, "decomposes-into"), TENANT);
        store.addEdge(EdgeInput.of(findArtifactId, visitBlacksmith2, "decomposes-into"), TENANT);

        int beforeCount = store.nodesIn(goalSgId, TENANT).size();
        new GoalResolutionPhase(store, (d, c, t) -> GoalDecompositionResult.EMPTY,
                (a, t) -> Map.of()).run(TENANT, List.of());
        int afterCount = store.nodesIn(goalSgId, TENANT).size();

        assertThat(afterCount).isLessThan(beforeCount);
    }

    // ── Phase 7: Revise dependencies ─────────────────────────────────

    @Test @Order(7)
    void phase7_reviseDependencies() {
        assertThat(store.getNode(shipV2Id, TENANT).property("status")).contains("blocked");

        store.updateNode(hireSeniorId,
                NodeUpdate.empty().withPropertiesToSet(Map.of("status", "completed")),
                TENANT);

        new GoalResolutionPhase(store, (d, c, t) -> GoalDecompositionResult.EMPTY,
                (a, t) -> Map.of()).run(TENANT, List.of());

        MindMapNode shipV2 = store.getNode(shipV2Id, TENANT);
        assertThat(shipV2.property("status")).contains("active");
    }

    // ── Phase 8: Goal affect ─────────────────────────────────────────

    @Test @Order(8)
    void phase8_goalAffect() {
        new GoalAffectPhase(store).run(TENANT, List.of());

        // Urgent goal — high arousal
        MindMapNode paper = store.getNode(submitPaperId, TENANT);
        assertThat(paper.arousal()).isNotNull();
        assertThat(paper.arousal()).isGreaterThan(0.0);

        // Completed goal — positive pleasure
        MindMapNode hired = store.getNode(hireSeniorId, TENANT);
        assertThat(hired.pleasure()).isNotNull();
        assertThat(hired.pleasure()).isGreaterThan(0.0);

        // Cooking goal (active, moderate urgency) — mild arousal
        MindMapNode cook = store.getNode(learnToCookId, TENANT);
        assertThat(cook.arousal()).isNotNull();
    }

    // ── Phase 9: Goal prioritization ─────────────────────────────────

    @Test @Order(9)
    void phase9_goalPrioritization() {
        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode paper = store.getNode(submitPaperId, TENANT);
        assertThat(paper.property("priority")).isPresent();
        double paperPriority = Double.parseDouble(paper.property("priority").get());
        assertThat(paperPriority).isBetween(0.0, 1.0);

        MindMapNode cook = store.getNode(learnToCookId, TENANT);
        assertThat(cook.property("priority")).isPresent();
        double cookPriority = Double.parseDouble(cook.property("priority").get());

        // High-urgency paper goal should outrank moderate cooking goal
        assertThat(paperPriority).isGreaterThan(cookPriority);
    }

    // ── Phase 10: Retrieval modulation ───────────────────────────────

    @Test @Order(10)
    void phase10_retrievalModulation() {
        // Create concept nodes at varying distances from active goals
        String directConcept = store.addNode(NodeInput.of("attention-mechanisms", conceptSgId)
                .withProperties(Map.of()), TENANT);
        store.addEdge(EdgeInput.of(directConcept, submitPaperId, "contributes-to"), TENANT);

        String distantConcept = store.addNode(NodeInput.of("quantum-computing", conceptSgId)
                .withProperties(Map.of()), TENANT);

        var factor = new GoalRelevanceModulationFactor(store, TENANT);

        // Memory about attention mechanisms — 1 edge from active goal
        Memory nearMemory = new Memory("m1", Subject.of("concept", "attention-mechanisms"),
                new MemoryDomain("experience"), TENANT, null,
                "Attention is all you need — seminal transformer paper",
                Map.of(), Instant.now(),
                new Confidence(ConfidenceOrigin.STATED, 0.9, null),
                null, null, null, null, Set.of());

        double nearWeight = factor.apply(nearMemory, MEMORY_PROFILE);
        assertThat(nearWeight).isEqualTo(1.0);

        // Memory about quantum computing — no path to any active goal
        Memory farMemory = new Memory("m2", Subject.of("concept", "quantum-computing"),
                new MemoryDomain("experience"), TENANT, null,
                "Quantum entanglement for cryptographic protocols",
                Map.of(), Instant.now(),
                new Confidence(ConfidenceOrigin.STATED, 0.9, null),
                null, null, null, null, Set.of());

        double farWeight = factor.apply(farMemory, MEMORY_PROFILE);
        assertThat(farWeight).isEqualTo(1.0); // neutral — no path found
    }
}
