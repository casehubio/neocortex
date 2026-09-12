package io.casehub.neocortex.examples.rag.analytics;

import io.casehub.neocortex.fusion.ScoreFusion;
import io.casehub.neocortex.rag.ColBertRelevanceEvaluator;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.CorrelationGraph;
import io.casehub.neocortex.rag.DocumentImpact;
import io.casehub.neocortex.rag.DocumentStats;
import io.casehub.neocortex.rag.QueryCluster;
import io.casehub.neocortex.rag.QueryQualitySignal;
import io.casehub.neocortex.rag.RelevanceGrade;
import io.casehub.neocortex.rag.RetrievalAnalyzer;
import io.casehub.neocortex.rag.RetrievalOutcome;
import io.casehub.neocortex.rag.RetrievalRecord;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.RetrievedDocumentRef;
import io.casehub.neocortex.rag.ScoredGrade;
import io.casehub.neocortex.rag.testing.InMemoryRetrievalTracker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Walkthrough: RAG retrieval analytics for a compliance knowledge base.
 *
 * Scenario — a compliance team manages a corpus of 10 regulatory documents.
 * Over time, analysts query the corpus for guidance on various regulations.
 * This walkthrough demonstrates how RetrievalAnalyzer reveals quality
 * patterns: which documents are valuable, which queries cluster together,
 * and which parts of the corpus are underperforming.
 *
 * Each test method is a phase in the analytics workflow:
 *   1. Populate retrieval history — simulate analyst queries with feedback
 *   2. Document statistics — per-document retrieval patterns
 *   3. Query-level analysis — find consistently poor queries (corpus gaps)
 *   4. Correlation graph — bipartite query↔document relationships
 *   5. Query clusters — discover related query groups
 *   6. Document impact — rank documents by centrality
 *   7. Score fusion comparison — RRF vs Convex Combination side by side
 *   8. Relevance calibration — ColBERT threshold derivation from data
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagAnalyticsWalkthroughTest {

    private InMemoryRetrievalTracker tracker;
    private CorpusRef corpus;

    private static final Instant BASE = Instant.EPOCH;
    private static final Instant WINDOW_END = Instant.now().plus(Duration.ofDays(1));

    // Document IDs — regulatory corpus
    private static final String DOC_GDPR_OVERVIEW = "gdpr-overview";
    private static final String DOC_GDPR_CONSENT = "gdpr-consent-requirements";
    private static final String DOC_GDPR_BREACH = "gdpr-breach-notification";
    private static final String DOC_SOX_CONTROLS = "sox-internal-controls";
    private static final String DOC_SOX_AUDIT = "sox-audit-procedures";
    private static final String DOC_HIPAA_PHI = "hipaa-phi-safeguards";
    private static final String DOC_HIPAA_ACCESS = "hipaa-access-controls";
    private static final String DOC_PCI_ENCRYPTION = "pci-encryption-standards";
    private static final String DOC_AML_KYC = "aml-kyc-procedures";
    private static final String DOC_OUTDATED_POLICY = "internal-policy-2019";

    @BeforeAll
    void setUp() {
        tracker = new InMemoryRetrievalTracker();
        corpus = new CorpusRef("compliance-team", "regulatory-corpus");

        populateRetrievalHistory();
    }

    // --- Phase 1: Populate retrieval history ---

    private void populateRetrievalHistory() {
        // GDPR queries — frequently asked, well-served by the corpus
        recordRetrieval("What are GDPR consent requirements?", 0,
            chunk(DOC_GDPR_CONSENT, 0.92), chunk(DOC_GDPR_OVERVIEW, 0.85));
        giveFeedback(0, DOC_GDPR_CONSENT, RetrievalOutcome.HIGHLY_RELEVANT);
        giveFeedback(0, DOC_GDPR_OVERVIEW, RetrievalOutcome.RELEVANT);

        recordRetrieval("GDPR data breach notification timeline", 1,
            chunk(DOC_GDPR_BREACH, 0.94), chunk(DOC_GDPR_OVERVIEW, 0.78));
        giveFeedback(1, DOC_GDPR_BREACH, RetrievalOutcome.HIGHLY_RELEVANT);
        giveFeedback(1, DOC_GDPR_OVERVIEW, RetrievalOutcome.RELEVANT);

        recordRetrieval("How to handle GDPR subject access requests", 2,
            chunk(DOC_GDPR_OVERVIEW, 0.88), chunk(DOC_GDPR_CONSENT, 0.72));
        giveFeedback(2, DOC_GDPR_OVERVIEW, RetrievalOutcome.RELEVANT);
        giveFeedback(2, DOC_GDPR_CONSENT, RetrievalOutcome.PARTIALLY_RELEVANT);

        recordRetrieval("GDPR consent withdrawal process", 3,
            chunk(DOC_GDPR_CONSENT, 0.90), chunk(DOC_GDPR_OVERVIEW, 0.80));
        giveFeedback(3, DOC_GDPR_CONSENT, RetrievalOutcome.HIGHLY_RELEVANT);

        // SOX queries — moderate usage
        recordRetrieval("SOX internal control requirements", 5,
            chunk(DOC_SOX_CONTROLS, 0.91), chunk(DOC_SOX_AUDIT, 0.75));
        giveFeedback(5, DOC_SOX_CONTROLS, RetrievalOutcome.HIGHLY_RELEVANT);
        giveFeedback(5, DOC_SOX_AUDIT, RetrievalOutcome.RELEVANT);

        recordRetrieval("How to prepare for SOX audit", 6,
            chunk(DOC_SOX_AUDIT, 0.88), chunk(DOC_SOX_CONTROLS, 0.70));
        giveFeedback(6, DOC_SOX_AUDIT, RetrievalOutcome.HIGHLY_RELEVANT);
        giveFeedback(6, DOC_SOX_CONTROLS, RetrievalOutcome.RELEVANT);

        // HIPAA queries
        recordRetrieval("HIPAA PHI protection requirements", 7,
            chunk(DOC_HIPAA_PHI, 0.93), chunk(DOC_HIPAA_ACCESS, 0.68));
        giveFeedback(7, DOC_HIPAA_PHI, RetrievalOutcome.HIGHLY_RELEVANT);

        recordRetrieval("HIPAA access control policies", 8,
            chunk(DOC_HIPAA_ACCESS, 0.85), chunk(DOC_HIPAA_PHI, 0.72));
        giveFeedback(8, DOC_HIPAA_ACCESS, RetrievalOutcome.RELEVANT);

        // The stale document — retrieved but consistently rated poorly
        recordRetrieval("Current internal data handling policy", 10,
            chunk(DOC_OUTDATED_POLICY, 0.65), chunk(DOC_GDPR_OVERVIEW, 0.55));
        giveFeedback(10, DOC_OUTDATED_POLICY, RetrievalOutcome.NOT_RELEVANT);
        giveFeedback(10, DOC_GDPR_OVERVIEW, RetrievalOutcome.PARTIALLY_RELEVANT);

        recordRetrieval("Company data retention policy", 11,
            chunk(DOC_OUTDATED_POLICY, 0.60), chunk(DOC_GDPR_OVERVIEW, 0.50));
        giveFeedback(11, DOC_OUTDATED_POLICY, RetrievalOutcome.NOT_RELEVANT);

        recordRetrieval("Internal policy on data classification", 12,
            chunk(DOC_OUTDATED_POLICY, 0.58));
        giveFeedback(12, DOC_OUTDATED_POLICY, RetrievalOutcome.NOT_RELEVANT);

        // A zero-hit query — reveals a corpus gap
        recordRetrieval("ISO 27001 certification requirements", 14);

        // A low-relevance query — results exist but don't help
        recordRetrieval("Cross-border data transfer mechanisms", 15,
            chunk(DOC_GDPR_OVERVIEW, 0.35));
        giveFeedback(15, DOC_GDPR_OVERVIEW, RetrievalOutcome.NOT_RELEVANT);

        // PCI query — single appearance, moderate relevance
        recordRetrieval("PCI DSS encryption requirements", 17,
            chunk(DOC_PCI_ENCRYPTION, 0.87));
        giveFeedback(17, DOC_PCI_ENCRYPTION, RetrievalOutcome.RELEVANT);

        // Another GDPR cluster query
        recordRetrieval("GDPR data processing agreements", 19,
            chunk(DOC_GDPR_CONSENT, 0.82), chunk(DOC_GDPR_OVERVIEW, 0.76));
        giveFeedback(19, DOC_GDPR_CONSENT, RetrievalOutcome.RELEVANT);
    }

    // --- Phase 2: Document statistics ---

    @Test @Order(1)
    void documentStatistics_revealRetrievalPatterns() {
        Map<String, DocumentStats> stats = RetrievalAnalyzer.documentStats(
            tracker, corpus, BASE, WINDOW_END);

        assertThat(stats).isNotEmpty();

        // GDPR overview is the most-retrieved document (appears in many queries)
        DocumentStats gdprOverview = stats.get(DOC_GDPR_OVERVIEW);
        assertThat(gdprOverview).isNotNull();
        assertThat(gdprOverview.retrievalCount()).isGreaterThanOrEqualTo(5);

        // GDPR consent has the highest feedback quality
        DocumentStats gdprConsent = stats.get(DOC_GDPR_CONSENT);
        assertThat(gdprConsent).isNotNull();
        int highlyRelevant = gdprConsent.feedbackDistribution()
            .getOrDefault(RetrievalOutcome.HIGHLY_RELEVANT, 0);
        assertThat(highlyRelevant).isGreaterThanOrEqualTo(2);

        // Outdated policy has consistently poor outcomes
        DocumentStats outdated = stats.get(DOC_OUTDATED_POLICY);
        assertThat(outdated).isNotNull();
        int notRelevant = outdated.feedbackDistribution()
            .getOrDefault(RetrievalOutcome.NOT_RELEVANT, 0);
        assertThat(notRelevant).isGreaterThanOrEqualTo(3);
        assertThat(outdated.averageRetrievalScore()).isLessThan(0.7);

        // AML/KYC document was never retrieved
        assertThat(stats).doesNotContainKey(DOC_AML_KYC);

        System.out.println("=== Document Statistics ===");
        stats.values().forEach(ds -> System.out.printf(
            "  %-30s  retrievals=%d  avgScore=%.2f  feedback=%s%n",
            ds.sourceDocumentId(), ds.retrievalCount(),
            ds.averageRetrievalScore(), ds.feedbackDistribution()));
    }

    // --- Phase 3: Query-level analysis ---

    @Test @Order(2)
    void queryAnalysis_findCorpusGaps() {
        // Zero-hit queries reveal topics the corpus doesn't cover at all
        List<QueryQualitySignal> zeroHits = RetrievalAnalyzer.zeroHitQueries(
            tracker, corpus, BASE, WINDOW_END);

        assertThat(zeroHits).isNotEmpty();
        assertThat(zeroHits).anyMatch(q ->
            q.queryText().contains("ISO 27001"));

        System.out.println("=== Zero-Hit Queries (corpus gaps) ===");
        zeroHits.forEach(q -> System.out.printf(
            "  \"%s\"  (asked %d times)%n", q.queryText(), q.retrievalCount()));

        // Low-relevance queries reveal topics where results exist but don't help
        List<QueryQualitySignal> lowRelevance = RetrievalAnalyzer.lowRelevanceQueries(
            tracker, corpus, BASE, WINDOW_END, 0.5);

        System.out.println("=== Low-Relevance Queries ===");
        lowRelevance.forEach(q -> System.out.printf(
            "  \"%s\"  avgScore=%.2f  (asked %d times)%n",
            q.queryText(), q.averageRelevanceScore(), q.retrievalCount()));
    }

    // --- Phase 4: Correlation graph ---

    @Test @Order(3)
    void correlationGraph_revealsQueryDocumentRelationships() {
        CorrelationGraph graph = RetrievalAnalyzer.correlationGraph(
            tracker, corpus, BASE, WINDOW_END);

        assertThat(graph.queries()).isNotEmpty();
        assertThat(graph.documents()).isNotEmpty();

        // GDPR overview should appear in many query contexts
        assertThat(graph.documents()).containsKey(DOC_GDPR_OVERVIEW);
        assertThat(graph.documents().get(DOC_GDPR_OVERVIEW).queryEdges()).hasSizeGreaterThanOrEqualTo(3);

        System.out.println("=== Correlation Graph ===");
        System.out.println("  Queries: " + graph.queries().size());
        System.out.println("  Documents: " + graph.documents().size());
        graph.documents().forEach((docId, node) -> {
            System.out.printf("  %s  connectedQueries=%d  totalRetrievals=%d%n",
                docId, node.queryEdges().size(), node.retrievalCount());
        });
    }

    // --- Phase 5: Query clusters ---

    @Test @Order(4)
    void queryClusters_discoverRelatedQueryGroups() {
        CorrelationGraph graph = RetrievalAnalyzer.correlationGraph(
            tracker, corpus, BASE, WINDOW_END);

        List<QueryCluster> clusters = RetrievalAnalyzer.queryClusters(graph, 0.3);

        // GDPR-related queries should cluster together (shared documents)
        assertThat(clusters).isNotEmpty();

        System.out.println("=== Query Clusters (Jaccard >= 0.3) ===");
        for (int i = 0; i < clusters.size(); i++) {
            QueryCluster c = clusters.get(i);
            System.out.printf("  Cluster %d (similarity=%.2f):%n", i + 1, c.jaccardSimilarity());
            c.queryTexts().forEach(q -> System.out.printf("    - \"%s\"%n", q));
            System.out.printf("    Shared docs: %s%n", c.sharedDocumentIds());
        }
    }

    // --- Phase 6: Document impact ---

    @Test @Order(5)
    void documentImpact_ranksByCentrality() {
        CorrelationGraph graph = RetrievalAnalyzer.correlationGraph(
            tracker, corpus, BASE, WINDOW_END);

        List<DocumentImpact> impact = RetrievalAnalyzer.documentImpact(graph);

        assertThat(impact).isNotEmpty();

        // GDPR overview should be highest-impact (most connected)
        DocumentImpact topDoc = impact.getFirst();
        assertThat(topDoc.documentId()).isEqualTo(DOC_GDPR_OVERVIEW);
        assertThat(topDoc.distinctQueryCount()).isGreaterThanOrEqualTo(3);

        System.out.println("=== Document Impact (ranked by centrality) ===");
        impact.forEach(di -> System.out.printf(
            "  %-30s  queries=%d  retrievals=%d  avgScore=%.2f  outcomes=%s%n",
            di.documentId(), di.distinctQueryCount(), di.totalRetrievals(),
            di.averageScore(), di.aggregateOutcomes()));
    }

    // --- Phase 7: Score fusion comparison ---

    @Test @Order(6)
    void scoreFusionComparison_rrfVsConvexCombination() {
        // Three retrieval legs with different rankings — simulating dense, sparse, BM25
        record ScoredDoc(String id, double score) {}

        // Dense embeddings favour semantic similarity
        var denseLeg = new ScoreFusion.ScoredLeg<>(
            List.of(
                new ScoredDoc("doc-A", 0.95),
                new ScoredDoc("doc-B", 0.80),
                new ScoredDoc("doc-C", 0.60),
                new ScoredDoc("doc-D", 0.40)),
            ScoredDoc::score, 1.0);

        // Sparse (SPLADE) favours exact term matches
        var sparseLeg = new ScoreFusion.ScoredLeg<>(
            List.of(
                new ScoredDoc("doc-C", 0.90),
                new ScoredDoc("doc-A", 0.70),
                new ScoredDoc("doc-E", 0.65),
                new ScoredDoc("doc-B", 0.30)),
            ScoredDoc::score, 1.0);

        // BM25 favours keyword frequency
        var bm25Leg = new ScoreFusion.ScoredLeg<>(
            List.of(
                new ScoredDoc("doc-B", 0.88),
                new ScoredDoc("doc-D", 0.82),
                new ScoredDoc("doc-A", 0.50),
                new ScoredDoc("doc-C", 0.45)),
            ScoredDoc::score, 0.5);

        var legs = List.of(denseLeg, sparseLeg, bm25Leg);

        // RRF — rank-based fusion, less sensitive to score magnitude
        var rrfResults = ScoreFusion.rrf(legs, ScoredDoc::id, 5, 60.0);

        // CC — score-based fusion, sensitive to absolute values
        var ccResults = ScoreFusion.convexCombination(legs, ScoredDoc::id, 5);

        assertThat(rrfResults).isNotEmpty();
        assertThat(ccResults).isNotEmpty();

        System.out.println("=== Score Fusion Comparison ===");
        System.out.println("  RRF (rank-based, k=60):");
        rrfResults.forEach(r -> System.out.printf(
            "    %s  score=%.4f%n", r.item().id(), r.score()));

        System.out.println("  Convex Combination (score-based):");
        ccResults.forEach(r -> System.out.printf(
            "    %s  score=%.4f%n", r.item().id(), r.score()));

        // doc-A appears in all three legs with high ranks — both methods should rank it highly
        assertThat(rrfResults.stream().map(r -> r.item().id()).toList())
            .contains("doc-A");
        assertThat(ccResults.stream().map(r -> r.item().id()).toList())
            .contains("doc-A");
    }

    // --- Phase 8: Relevance calibration ---

    @Test @Order(7)
    void relevanceCalibration_deriveThresholdsFromData() {
        // Simulate a score distribution from cross-encoder or ColBERT scoring
        List<Double> sampleScores = new ArrayList<>();
        // High-quality matches (cluster around 0.8-0.95)
        DoubleStream.of(0.92, 0.88, 0.95, 0.87, 0.90, 0.85, 0.91, 0.89).forEach(sampleScores::add);
        // Ambiguous matches (0.5-0.7)
        DoubleStream.of(0.65, 0.58, 0.62, 0.55, 0.68, 0.52).forEach(sampleScores::add);
        // Poor matches (0.1-0.4)
        DoubleStream.of(0.15, 0.22, 0.35, 0.18, 0.28, 0.12).forEach(sampleScores::add);

        // Calibrate using P75/P25 — the default percentile split
        ColBertRelevanceEvaluator evaluator = ColBertRelevanceEvaluator.calibrate(sampleScores);

        // Evaluate synthetic chunks to see grading in action
        var highChunk = new RetrievedChunk("GDPR Article 7 consent...", "gdpr-consent", 0.92, Map.of());
        var ambiguousChunk = new RetrievedChunk("Data processing...", "gdpr-overview", 0.60, Map.of());
        var poorChunk = new RetrievedChunk("Outdated policy v2019...", "old-policy", 0.20, Map.of());

        List<ScoredGrade> grades = evaluator.evaluateChunks("consent requirements",
            List.of(highChunk, ambiguousChunk, poorChunk));

        assertThat(grades).hasSize(3);
        assertThat(grades.get(0).grade()).isEqualTo(RelevanceGrade.CORRECT);
        assertThat(grades.get(1).grade()).isEqualTo(RelevanceGrade.AMBIGUOUS);
        assertThat(grades.get(2).grade()).isEqualTo(RelevanceGrade.INCORRECT);

        System.out.println("=== Relevance Calibration ===");
        System.out.println("  Calibrated from " + sampleScores.size() + " sample scores");
        for (int i = 0; i < grades.size(); i++) {
            ScoredGrade g = grades.get(i);
            System.out.printf("    score=%.2f → %s%n", g.score(), g.grade());
        }
    }

    // --- Helpers ---

    private final List<String> retrievalIds = new ArrayList<>();

    private void recordRetrieval(String queryText, int dayOffset, RetrievedChunk... chunks) {
        String id = tracker.record(
            RetrievalQuery.of(queryText), corpus,
            List.of(chunks), 10);
        // Ensure stable ordering for retrieval ID lookup
        while (retrievalIds.size() <= dayOffset) retrievalIds.add(null);
        retrievalIds.set(dayOffset, id);
    }

    private void giveFeedback(int dayOffset, String docId, RetrievalOutcome outcome) {
        String retrievalId = retrievalIds.get(dayOffset);
        if (retrievalId != null) {
            tracker.feedback(retrievalId, docId, outcome);
        }
    }

    private static RetrievedChunk chunk(String docId, double score) {
        return new RetrievedChunk("Content from " + docId, docId, score, Map.of());
    }
}
