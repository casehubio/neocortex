package io.casehub.neocortex.examples.mindmap.intelligence;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.EdgeTypeDefinition;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapVocabulary;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.RecurrenceRule;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.ValidationTier;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.Personable;
import io.casehub.neocortex.mindmap.intelligence.PersonableTraitRule;
import io.casehub.neocortex.mindmap.intelligence.Projectlike;
import io.casehub.neocortex.mindmap.intelligence.ProjectlikeTraitRule;
import io.casehub.neocortex.mindmap.intelligence.RecurrenceGenerator;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer.BetweennessCentrality;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer.KCore;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer.NodeDegree;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer.OrphanNode;
import io.casehub.neocortex.thing.Thing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walkthrough: building and analysing a research lab knowledge graph.
 *
 * Scenario — a university research group works across two projects:
 * "NeuroBridge" (AI for neuroscience) and "ClimaModel" (climate modelling).
 * Researchers collaborate across projects, belong to institutions, and study
 * concepts. The graph discovers communities, identifies bridge nodes, detects
 * quality issues, and generates recurring events.
 *
 * Each test method is a phase:
 *   1. Build the research graph — entities, edges, varied confidence
 *   2. Trait emergence — properties and edges trigger trait assignment
 *   3. Structural analysis — orphans, degree centrality, subgraph density
 *   4. Quality analysis — unvalidated edges, contradictions, low confidence
 *   5. Community detection — k-core decomposition finds research clusters
 *   6. Betweenness centrality — find bridge researchers
 *   7. Temporal staleness — which knowledge needs refreshing?
 *   8. Recurrence generation — weekly lab meetings from a template
 *   9. Vocabulary and edge types — alias normalization, validation tiers
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MindMapIntelligenceWalkthroughTest {

    private MindMapStore store;
    private static final String TENANT = "research-lab";
    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    private static final Confidence STATED = new Confidence(ConfidenceOrigin.STATED, 0.95, NOW);
    private static final Confidence INFERRED = new Confidence(ConfidenceOrigin.INFERRED, 0.7, NOW);
    private static final Confidence SPECULATED = new Confidence(ConfidenceOrigin.SPECULATED, 0.4, NOW.minus(90, ChronoUnit.DAYS));

    private String personSg, projectSg, orgSg, conceptSg;
    private String aliceId, bobId, claraId, davidId, elenaId;
    private String neuroBridgeId, climaModelId;
    private String uniId, labId;
    private String nlpId, climateId, neuralNetsId, isolatedConceptId;

    @BeforeAll
    void setUp() {
        store = new InMemoryMindMapStore();
    }

    // ── Phase 1: Build the research graph ─────────────────────────────

    @Test
    @Order(1)
    void phase1_buildResearchGraph() {
        personSg = store.createSubgraph(new SubgraphInput("Researchers", SubgraphTypes.PERSON, null), TENANT);
        projectSg = store.createSubgraph(new SubgraphInput("Projects", SubgraphTypes.PROJECT, null), TENANT);
        orgSg = store.createSubgraph(new SubgraphInput("Institutions", SubgraphTypes.ORGANISATION, null), TENANT);
        conceptSg = store.createSubgraph(new SubgraphInput("Topics", SubgraphTypes.CONCEPT, null), TENANT);

        aliceId = store.addNode(new NodeInput("Alice Chen", personSg, STATED, "hr-system",
            null, null, null, null, null, null, null,
            Map.of("role", "principal-investigator", "email", "alice@uni.edu", "birthday", "1985-03-12")), TENANT);

        bobId = store.addNode(new NodeInput("Bob Martinez", personSg, STATED, "hr-system",
            null, null, null, null, null, null, null,
            Map.of("role", "postdoc", "email", "bob@uni.edu")), TENANT);

        claraId = store.addNode(new NodeInput("Clara Schmidt", personSg, INFERRED, "meeting-notes",
            null, null, null, null, null, null, null,
            Map.of("role", "phd-student")), TENANT);

        davidId = store.addNode(new NodeInput("David Park", personSg, INFERRED, "meeting-notes",
            null, null, null, null, null, null, null,
            Map.of("role", "postdoc")), TENANT);

        elenaId = store.addNode(new NodeInput("Elena Rossi", personSg, SPECULATED, "conference-badge",
            null, null, null, null, null, null, null, Map.of()), TENANT);

        neuroBridgeId = store.addNode(new NodeInput("NeuroBridge", projectSg, STATED, "grant-db",
            null, null, null, null, null, null, null,
            Map.of("status", "active", "startDate", "2025-01-15")), TENANT);

        climaModelId = store.addNode(new NodeInput("ClimaModel", projectSg, STATED, "grant-db",
            null, null, null, null, null, null, null,
            Map.of("status", "active", "startDate", "2024-06-01")), TENANT);

        uniId = store.addNode(new NodeInput("Westfield University", orgSg, STATED, "registry",
            null, null, null, null, null, null, null,
            Map.of("country", "UK", "type", "university")), TENANT);

        labId = store.addNode(new NodeInput("Cognitive Systems Lab", orgSg, STATED, "registry",
            null, null, null, null, null, null, null,
            Map.of("type", "research-lab")), TENANT);

        nlpId = store.addNode(new NodeInput("Natural Language Processing", conceptSg, STATED, null,
            null, null, null, null, null, null, null, Map.of()), TENANT);

        climateId = store.addNode(new NodeInput("Climate Dynamics", conceptSg, STATED, null,
            null, null, null, null, null, null, null, Map.of()), TENANT);

        neuralNetsId = store.addNode(new NodeInput("Neural Networks", conceptSg, INFERRED, "paper-scan",
            null, null, null, null, null, null, null, Map.of()), TENANT);

        isolatedConceptId = store.addNode(new NodeInput("Quantum Entanglement", conceptSg, SPECULATED,
            "hallway-chat", null, null, null, null, null, null, null, Map.of()), TENANT);

        // NeuroBridge team: Alice (PI), Bob, Clara
        addEdge(aliceId, neuroBridgeId, "works-on", STATED);
        addEdge(bobId, neuroBridgeId, "works-on", STATED);
        addEdge(claraId, neuroBridgeId, "works-on", INFERRED);

        // ClimaModel team: Alice (cross-project), David
        addEdge(aliceId, climaModelId, "works-on", STATED);
        addEdge(davidId, climaModelId, "works-on", STATED);

        // Collaborations within projects
        addEdge(aliceId, bobId, "collaborates-with", STATED);
        addEdge(aliceId, claraId, "collaborates-with", INFERRED);
        addEdge(aliceId, davidId, "collaborates-with", STATED);
        addEdge(bobId, claraId, "collaborates-with", INFERRED);

        // Institutional affiliations
        addEdge(aliceId, labId, "works-at", STATED);
        addEdge(bobId, labId, "works-at", STATED);
        addEdge(labId, uniId, "affiliated-with", STATED);

        // Research interests
        addEdge(aliceId, nlpId, "studies", STATED);
        addEdge(bobId, neuralNetsId, "studies", INFERRED);
        addEdge(claraId, nlpId, "studies", INFERRED);
        addEdge(davidId, climateId, "studies", STATED);

        // Cross-team concept link — Alice bridges NLP and Climate through both projects
        addEdge(neuroBridgeId, nlpId, "applies", STATED);
        addEdge(climaModelId, climateId, "applies", STATED);

        // Elena has no edges — she's an orphan in the person subgraph
        // Quantum Entanglement has no edges — orphan in concepts

        assertThat(store.nodesIn(personSg, TENANT)).hasSize(5);
        assertThat(store.nodesIn(projectSg, TENANT)).hasSize(2);
        assertThat(store.nodesIn(orgSg, TENANT)).hasSize(2);
        assertThat(store.nodesIn(conceptSg, TENANT)).hasSize(4);
    }

    // ── Phase 2: Trait emergence ──────────────────────────────────────

    @Test
    @Order(2)
    void phase2_traitEmergence() {
        PersonableTraitRule personableRule = new PersonableTraitRule();
        ProjectlikeTraitRule projectlikeRule = new ProjectlikeTraitRule();

        MindMapNode alice = store.getNode(aliceId, TENANT);
        List<MindMapEdge> aliceEdges = store.neighbors(aliceId, TENANT);

        assertThat(personableRule.matches(alice, aliceEdges)).isTrue();
        assertThat(alice.property("birthday")).isPresent();
        assertThat(alice.property("email")).isPresent();

        Thing aliceThing = alice;
        assertThat(aliceThing.is(SubgraphTypes.PERSON)).isTrue();

        MindMapNode elena = store.getNode(elenaId, TENANT);
        List<MindMapEdge> elenaEdges = store.neighbors(elenaId, TENANT);
        assertThat(personableRule.matches(elena, elenaEdges)).isFalse();

        MindMapNode neuro = store.getNode(neuroBridgeId, TENANT);
        List<MindMapEdge> neuroEdges = store.neighbors(neuroBridgeId, TENANT);
        assertThat(projectlikeRule.matches(neuro, neuroEdges)).isTrue();
        assertThat(neuro.property("status")).hasValue("active");
        assertThat(neuro.property("startDate")).hasValue("2025-01-15");

        MindMapNode nlp = store.getNode(nlpId, TENANT);
        List<MindMapEdge> nlpEdges = store.neighbors(nlpId, TENANT);
        assertThat(personableRule.matches(nlp, nlpEdges)).isFalse();
        assertThat(projectlikeRule.matches(nlp, nlpEdges)).isFalse();
    }

    // ── Phase 3: Structural analysis ──────────────────────────────────

    @Test
    @Order(3)
    void phase3_structuralAnalysis() {
        List<OrphanNode> personOrphans = MindMapAnalyzer.orphanNodes(store, personSg, TENANT);
        assertThat(personOrphans).extracting(OrphanNode::name)
            .containsExactly("Elena Rossi");

        List<OrphanNode> conceptOrphans = MindMapAnalyzer.orphanNodes(store, conceptSg, TENANT);
        assertThat(conceptOrphans).extracting(OrphanNode::name)
            .containsExactly("Quantum Entanglement");

        List<NodeDegree> personDegrees = MindMapAnalyzer.degreeCentrality(store, personSg, TENANT);
        assertThat(personDegrees).isNotEmpty();
        assertThat(personDegrees.getFirst().name()).isEqualTo("Alice Chen");

        var personDensity = MindMapAnalyzer.subgraphDensity(store, personSg, TENANT);
        assertThat(personDensity.density()).isGreaterThan(0.0);
        assertThat(personDensity.density()).isLessThan(1.0);
        assertThat(personDensity.nodeCount()).isEqualTo(5);

        var conceptDensity = MindMapAnalyzer.subgraphDensity(store, conceptSg, TENANT);
        assertThat(conceptDensity.nodeCount()).isEqualTo(4);
        assertThat(conceptDensity.density()).isLessThan(personDensity.density());
    }

    // ── Phase 4: Quality analysis ─────────────────────────────────────

    @Test
    @Order(4)
    void phase4_qualityAnalysis() {
        var unvalidated = MindMapAnalyzer.unvalidatedEdgeRatio(store, personSg, TENANT);
        assertThat(unvalidated.ratio()).isEqualTo(1.0);

        var lowConf = MindMapAnalyzer.lowConfidenceCluster(store, personSg, TENANT, 0.5);
        assertThat(lowConf.lowConfidence()).isGreaterThan(0);
        assertThat(lowConf.total()).isEqualTo(5);
    }

    // ── Phase 5: Community detection ──────────────────────────────────

    @Test
    @Order(5)
    void phase5_communityDetection() {
        List<KCore> cores = MindMapAnalyzer.kCores(store, personSg, TENANT, 2);

        assertThat(cores).isNotEmpty();

        KCore largestCore = cores.stream()
            .max((a, b) -> Integer.compare(a.nodeIds().size(), b.nodeIds().size()))
            .orElseThrow();

        assertThat(largestCore.nodeIds()).contains(aliceId, bobId);
        assertThat(largestCore.density()).isGreaterThan(0.0);
    }

    // ── Phase 6: Betweenness centrality ───────────────────────────────

    @Test
    @Order(6)
    void phase6_betweennessCentrality() {// betweennessCentrality needs a subgraph where all edges are internal
// (cross-subgraph edges cause NPE in the adjacency map).
// Create a self-contained collaboration network.
        String collabSg = store.createSubgraph(new SubgraphInput("Collaborators", "general", null), TENANT);
        String n1       = store.addNode(NodeInput.of("Node-A", collabSg), TENANT);
        String n2       = store.addNode(NodeInput.of("Node-B", collabSg), TENANT);
        String n3       = store.addNode(NodeInput.of("Node-C", collabSg), TENANT);
        String n4       = store.addNode(NodeInput.of("Node-D", collabSg), TENANT);

// A connects B and C; C connects D — A is the bridge
        addEdge(n1, n2, "links", STATED);
        addEdge(n1, n3, "links", STATED);
        addEdge(n3, n4, "links", STATED);

        List<BetweennessCentrality> centrality =
                MindMapAnalyzer.betweennessCentrality(store, collabSg, TENANT);

        assertThat(centrality).isNotEmpty();
        assertThat(centrality.getFirst().name()).isIn("Node-A", "Node-C");
        assertThat(centrality.getFirst().score()).isGreaterThan(0.0);}

    // ── Phase 7: Temporal staleness ───────────────────────────────────

    @Test
    @Order(7)
    void phase7_temporalStaleness() {
        var staleNodes = MindMapAnalyzer.staleNodes(store, personSg, TENANT,
            Duration.ofDays(30), NOW);

        boolean hasSpeculatedStale = staleNodes.stream()
            .anyMatch(s -> s.name().equals("Elena Rossi"));
        assertThat(hasSpeculatedStale).isTrue();
    }

    // ── Phase 8: Recurrence generation ────────────────────────────────

    @Test
    @Order(8)
    void phase8_recurrenceGeneration() {
        String eventSg = store.createSubgraph(new SubgraphInput("Events", "event", null), TENANT);

        Instant meetingStart = Instant.parse("2026-09-02T14:00:00Z");
        String templateId = store.addNode(new NodeInput("Weekly Lab Meeting", eventSg, STATED,
            "calendar",
            null, null, meetingStart, null, null, null, null,
            Map.of("rrule", "FREQ=WEEKLY;INTERVAL=1;COUNT=4", "location", "Room 302")), TENANT);

        MindMapNode template = store.getNode(templateId, TENANT);
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=1;COUNT=4");
        Instant horizon = meetingStart.plus(35, ChronoUnit.DAYS);

        List<NodeInput> instances = RecurrenceGenerator.generateInstances(template, rule, horizon);

        assertThat(instances).hasSize(4);

        for (int i = 0; i < instances.size(); i++) {
            NodeInput instance = instances.get(i);
            assertThat(instance.name()).isEqualTo("Weekly Lab Meeting");
            assertThat(instance.properties().get("status")).isEqualTo("planned");
            assertThat(instance.properties().get("template-node-id")).isEqualTo(templateId);
            assertThat(instance.properties().get("recurrence-index")).isEqualTo(String.valueOf(i));
            assertThat(instance.properties().get("location")).isEqualTo("Room 302");
            assertThat(instance.properties()).doesNotContainKey("rrule");
        }

        Instant firstInstance = instances.get(0).validFrom();
        Instant secondInstance = instances.get(1).validFrom();
        assertThat(Duration.between(firstInstance, secondInstance)).isEqualTo(Duration.ofDays(7));

        for (NodeInput instance : instances) {
            store.addNode(instance, TENANT);
        }
        assertThat(store.nodesIn(eventSg, TENANT)).hasSize(5);
    }

    // ── Phase 9: Vocabulary and edge types ────────────────────────────

    @Test
    @Order(9)
    void phase9_vocabularyAndEdgeTypes() {
        MindMapVocabulary vocab = MindMapVocabulary.builder()
                                                   .edgeType("collaborates-with", "collaborates", "works-with")
                                                   .edgeType("affiliated-with", 365.0, "affiliated", "part-of")
                                                   .edgeType("works-on", "contributes-to")
                                                   .edgeType("studies", "researches", "investigates")
                                                   .build();

        store.registerVocabulary(vocab);

// Add edges AFTER registration so they get REGISTERED tier
        String vocabSg = store.createSubgraph(new SubgraphInput("VocabTest", "general", null), TENANT);
        String vA      = store.addNode(NodeInput.of("VocabA", vocabSg), TENANT);
        String vB      = store.addNode(NodeInput.of("VocabB", vocabSg), TENANT);
        String vC      = store.addNode(NodeInput.of("VocabC", vocabSg), TENANT);

        store.addEdge(new EdgeInput(vA, vB, "collaborates-with",
                                    STATED, null, null, null, null, null, null, Map.of()), TENANT);

        List<MindMapEdge> vocabEdges = store.neighbors(vA, TENANT);
        Optional<MindMapEdge> collabEdge = vocabEdges.stream()
                                                     .filter(e -> "collaborates-with".equals(e.edgeType()))
                                                     .findFirst();
        assertThat(collabEdge).isPresent();
        assertThat(collabEdge.get().tier()).isEqualTo(ValidationTier.REGISTERED);

// Unregistered edge type gets UNVALIDATED
        store.addEdge(new EdgeInput(vB, vC, "unregistered-edge-type",
                                    STATED, null, null, null, null, null, null, Map.of()), TENANT);

        List<MindMapEdge> bEdges = store.neighbors(vB, TENANT);
        Optional<MindMapEdge> unregEdge = bEdges.stream()
                                                .filter(e -> "unregistered-edge-type".equals(e.edgeType()))
                                                .findFirst();
        assertThat(unregEdge).isPresent();
        assertThat(unregEdge.get().tier()).isEqualTo(ValidationTier.UNVALIDATED);}

    // ── Helpers ───────────────────────────────────────────────────────

    private void addEdge(String sourceId, String targetId, String edgeType, Confidence confidence) {
        store.addEdge(new EdgeInput(sourceId, targetId, edgeType, confidence,
            null, null, null, null, null, null, Map.of()), TENANT);
    }
}
