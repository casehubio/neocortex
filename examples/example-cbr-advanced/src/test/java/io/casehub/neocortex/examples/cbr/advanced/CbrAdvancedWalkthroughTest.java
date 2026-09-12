package io.casehub.neocortex.examples.cbr.advanced;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrSimilarityScorer;
import io.casehub.neocortex.memory.cbr.DtwSimilarity;
import io.casehub.neocortex.memory.cbr.EditDistanceResult;
import io.casehub.neocortex.memory.cbr.EditDistanceSimilarity;
import io.casehub.neocortex.memory.cbr.EditOp;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.RetrievalMode;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import io.casehub.neocortex.memory.cbr.SupersessionStatus;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import io.casehub.neocortex.memory.cbr.TrendAnalyzer;
import io.casehub.neocortex.memory.cbr.TrendSpec;
import io.casehub.neocortex.memory.cbr.TrendType;
import io.casehub.neocortex.memory.cbr.WarpingConstraint;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import io.casehub.neocortex.fusion.FusionStrategy;
import io.casehub.platform.api.path.Path;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Walkthrough: advanced CBR features for clinical decision support.
 *
 * Scenario — a hospital's clinical decision support system matches
 * incoming patients against historical cases by vital sign time series
 * (heart rate trajectories), treatment step sequences, and structured
 * clinical features. The system learns from outcomes, manages case
 * lifecycle via supersession, and scopes retrieval by ward hierarchy.
 *
 * Each test method demonstrates a distinct capability:
 *   1. Schema with temporal fields — TimeSeries, DiscreteSequence, TrendSpec
 *   2. Store cases with vital sign trajectories
 *   3. Retrieve similar cases — DTW on heart rate, weighted scoring
 *   4. Temporal decay — older cases score lower
 *   5. Filters and hierarchical scoping
 *   6. Outcome feedback — EMA confidence adjustment
 *   7. Supersession and reinstatement
 *   8. Edit distance on treatment step sequences
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CbrAdvancedWalkthroughTest {

    private InMemoryCbrCaseMemoryStore store;

    static final MemoryDomain DOMAIN = new MemoryDomain("clinical");
    static final String TENANT = "hospital-demo";
    static final String CASE_TYPE = "patient-case";
    static final Path SCOPE = Path.of("/hospital/cardiology/ward-3");
    static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    // Case IDs populated during phases
    private String improvingCaseId;
    private String worseningCaseId;
    private String stableCaseId;

    // Schema with temporal fields
    static final CbrFeatureSchema SCHEMA = new CbrFeatureSchema(CASE_TYPE, List.of(
        FeatureField.numeric("age", 0, 120),
        FeatureField.categorical("diagnosis"),
        FeatureField.categorical("ward"),
        FeatureField.numeric("severity", 1, 10, new SimilaritySpec.GaussianDecay(2.0)),
        new FeatureField.TimeSeries("heartRate",
            List.of(
                new FeatureField.Numeric("timestamp", 0, Long.MAX_VALUE),
                new FeatureField.Numeric("bpm", 40, 200)
            ),
            "timestamp",
            new SimilaritySpec.DtwSpec(new WarpingConstraint.SakoeChibaBand(2)),
            new TrendSpec(Set.of(TrendType.SLOPE, TrendType.VOLATILITY), ChronoUnit.HOURS)),
        new FeatureField.DiscreteSequence("treatmentSteps",
            new SimilaritySpec.EditDistanceSpec(
                Map.of("beta-blocker", Map.of("calcium-blocker", 0.7)),
                null, null))
    ));

    @BeforeAll
    void setUp() {
        store = new InMemoryCbrCaseMemoryStore();
        store.registerSchema(SCHEMA);
    }

    // ─── Phase 1: Schema registration ────────────────────────────────────

    @Test @Order(1)
    void schemaRegistersTemporalFieldsWithDtwAndTrends() {
        // The schema was registered in setUp. Verify it accepted TimeSeries
        // with DtwSpec + TrendSpec, and DiscreteSequence with EditDistanceSpec.
        // The real test is that registerSchema didn't throw — the validation
        // in FeatureField.TimeSeries enforces:
        //   - at least one non-timestamp Numeric inner field (bpm)
        //   - SimilaritySpec must be DtwSpec (not GaussianDecay etc.)
        //   - TrendSpec types are non-empty

        assertThat(SCHEMA.fields()).hasSize(6);
        assertThat(SCHEMA.fields().stream().map(FeatureField::name))
            .containsExactly("age", "diagnosis", "ward", "severity", "heartRate", "treatmentSteps");
    }

    // ─── Phase 2: Store cases with time series ───────────────────────────

    @Test @Order(2)
    void storeCasesWithVitalSignTrajectories() {
        // Patient A: improving heart rate (tachycardia resolving)
        improvingCaseId = storeCase(
            "Tachycardia in 68yo post-surgery, heart rate trending down over 6 hours",
            "Beta-blocker titration + fluid resuscitation. Heart rate normalised by hour 8.",
            features(
                "age", FeatureValue.number(68),
                "diagnosis", FeatureValue.string("post-operative-tachycardia"),
                "ward", FeatureValue.string("cardiology"),
                "severity", FeatureValue.number(7),
                "heartRate", hrSeries(
                    new double[]{0, 130}, new double[]{1, 122}, new double[]{2, 115},
                    new double[]{3, 108}, new double[]{4, 100}, new double[]{5, 92})
,
                "treatmentSteps", FeatureValue.stringList("assess", "ecg", "beta-blocker", "monitor", "discharge")),
            NOW.minus(Duration.ofDays(5)));

        // Patient B: worsening heart rate (deteriorating)
        worseningCaseId = storeCase(
            "Chest pain in 72yo, heart rate climbing despite intervention",
            "Escalated to ICU after beta-blocker failed. Angioplasty performed.",
            features(
                "age", FeatureValue.number(72),
                "diagnosis", FeatureValue.string("acute-coronary-syndrome"),
                "ward", FeatureValue.string("cardiology"),
                "severity", FeatureValue.number(9),
                "heartRate", hrSeries(
                    new double[]{0, 95}, new double[]{1, 102}, new double[]{2, 110},
                    new double[]{3, 118}, new double[]{4, 125}, new double[]{5, 135})
,
                "treatmentSteps", FeatureValue.stringList("assess", "ecg", "beta-blocker", "icu-transfer", "angioplasty")),
            NOW.minus(Duration.ofDays(30)));

        // Patient C: stable heart rate (routine monitoring)
        stableCaseId = storeCase(
            "Routine post-surgery monitoring, 55yo, stable vitals throughout",
            "Standard monitoring. Discharged after 24h observation.",
            features(
                "age", FeatureValue.number(55),
                "diagnosis", FeatureValue.string("post-operative-monitoring"),
                "ward", FeatureValue.string("cardiology"),
                "severity", FeatureValue.number(3),
                "heartRate", hrSeries(
                    new double[]{0, 78}, new double[]{1, 80}, new double[]{2, 79},
                    new double[]{3, 77}, new double[]{4, 81}, new double[]{5, 78})
,
                "treatmentSteps", FeatureValue.stringList("assess", "ecg", "monitor", "discharge")),
            NOW.minus(Duration.ofDays(2)));

        assertThat(improvingCaseId).isNotNull();
        assertThat(worseningCaseId).isNotNull();
        assertThat(stableCaseId).isNotNull();
    }

    // ─── Phase 3: Retrieve by similarity ─────────────────────────────────

    @Test @Order(3)
    void retrieveSimilarCasesByDtwOnHeartRate() {
        // New patient: heart rate trending down — most similar to Patient A
        var queryFeatures = features(
            "age", FeatureValue.number(65),
            "diagnosis", FeatureValue.string("post-operative-tachycardia"),
            "ward", FeatureValue.string("cardiology"),
            "severity", FeatureValue.number(6),
            "heartRate", hrSeries(
                new double[]{0, 128}, new double[]{1, 120}, new double[]{2, 112},
                new double[]{3, 105}, new double[]{4, 98}, new double[]{5, 90})
,
            "treatmentSteps", FeatureValue.stringList("assess", "ecg", "beta-blocker", "monitor"));

        var query = CbrQuery.of(TENANT, DOMAIN, SCOPE, CASE_TYPE, queryFeatures, 10)
            .withRetrievalMode(RetrievalMode.FEATURE_ONLY)
            .withWeights(Map.of("heartRate", 3.0, "severity", 2.0, "age", 1.0, "diagnosis", 1.5));

        List<ScoredCbrCase<FeatureVectorCbrCase>> results =
            store.retrieveSimilar(query, FeatureVectorCbrCase.class);

        assertThat(results).isNotEmpty();

        // The improving trajectory (Patient A) should rank highest
        // because its heart rate curve closely matches the query's downward trend
        assertThat(results.getFirst().caseId()).isEqualTo(improvingCaseId);

        // Show per-field similarity breakdown
        var breakdown = CbrSimilarityScorer.scoreDetailed(
            queryFeatures,
            results.getFirst().cbrCase().features(),
            query.weights(), SCHEMA, Map.of());

        assertThat(breakdown.featureSimilarities().get("heartRate")).isGreaterThan(0.0);

        System.out.println("=== Phase 3: Similarity Retrieval ===");
        for (var scored : results) {
            System.out.printf("  %.3f — %s%n", scored.score(),
                scored.cbrCase().problem().substring(0, Math.min(60, scored.cbrCase().problem().length())));
        }
        System.out.printf("  Heart rate similarity (top match): %.3f%n",
            breakdown.featureSimilarities().get("heartRate"));
    }

    // ─── Phase 4: Temporal decay ─────────────────────────────────────────

    @Test @Order(4)
    void temporalDecayPenalisesOlderCases() {
        // TemporalDecay is applied by the CDI decorator chain in production.
        // Without CDI, we demonstrate the decay math directly — this is what
        // TemporalDecayCbrCaseMemoryStore applies to each retrieved score.

        var halfLife = new TemporalDecay.HalfLife(Duration.ofDays(7));

        // The stable case (2 days old) — barely decayed
        double recentFactor = halfLife.factor(NOW.minus(Duration.ofDays(2)), NOW);
        // The worsening case (30 days old) — heavily decayed
        double oldFactor = halfLife.factor(NOW.minus(Duration.ofDays(30)), NOW);

        assertThat(recentFactor).isGreaterThan(0.7);
        assertThat(oldFactor).isLessThan(0.1);
        assertThat(recentFactor).isGreaterThan(oldFactor);

        // Three decay strategies available
        var linear = new TemporalDecay.Linear(Duration.ofDays(60));
        double linearOld = linear.factor(NOW.minus(Duration.ofDays(30)), NOW);
        assertThat(linearOld).isCloseTo(0.5, within(0.01));

        var step = new TemporalDecay.Step(Duration.ofDays(14), 0.3);
        double stepRecent = step.factor(NOW.minus(Duration.ofDays(2)), NOW);
        double stepOld = step.factor(NOW.minus(Duration.ofDays(30)), NOW);
        assertThat(stepRecent).isEqualTo(1.0);
        assertThat(stepOld).isEqualTo(0.3);

        System.out.println("\n=== Phase 4: Temporal Decay ===");
        System.out.printf("  HalfLife(7d) — 2-day-old: %.3f, 30-day-old: %.3f%n", recentFactor, oldFactor);
        System.out.printf("  Linear(60d)  — 30-day-old: %.3f%n", linearOld);
        System.out.printf("  Step(14d, 0.3) — 2-day: %.3f, 30-day: %.3f%n", stepRecent, stepOld);
    }

    // ─── Phase 5: Filters and scoping ────────────────────────────────────

    @Test @Order(5)
    void filterByWardAndUseHierarchicalScoping() {
        // Store a case in a different ward
        storeCase(
            "General surgery patient with stable vitals",
            "Standard monitoring. Discharged after 12h.",
            features(
                "age", FeatureValue.number(45),
                "diagnosis", FeatureValue.string("post-operative-monitoring"),
                "ward", FeatureValue.string("general-surgery"),
                "severity", FeatureValue.number(2),
                "heartRate", hrSeries(
                    new double[]{0, 75}, new double[]{1, 76}, new double[]{2, 74}),
                "treatmentSteps", FeatureValue.stringList("assess", "monitor", "discharge")),
            NOW.minus(Duration.ofDays(1)));

        // Query filtering to cardiology only
        var queryFeatures = features(
            "age", FeatureValue.number(60),
            "diagnosis", FeatureValue.string("post-operative-tachycardia"),
            "ward", FeatureValue.string("cardiology"),
            "severity", FeatureValue.number(5),
            "heartRate", hrSeries(new double[]{0, 100}, new double[]{1, 95}),
            "treatmentSteps", FeatureValue.stringList("assess", "ecg"));

        var filteredQuery = CbrQuery.of(TENANT, DOMAIN, SCOPE, CASE_TYPE, queryFeatures, 10)
            .withRetrievalMode(RetrievalMode.FEATURE_ONLY)
            .withFilter("ward", new CbrFilter.Contains("cardiology"));

        var results = store.retrieveSimilar(filteredQuery, FeatureVectorCbrCase.class);

        // General surgery case excluded by filter
        assertThat(results).allSatisfy(r ->
            assertThat(r.cbrCase().features().get("ward"))
                .isEqualTo(FeatureValue.string("cardiology")));

        System.out.println("\n=== Phase 5: Filtered Results ===");
        System.out.printf("  Cardiology-only results: %d cases%n", results.size());
    }

    // ─── Phase 6: Outcome feedback ───────────────────────────────────────

    @Test @Order(6)
    void outcomesFeedbackAdjustsConfidenceViaEma() {
        // Record a successful outcome for the improving case
        var successOutcome = CbrOutcome.of(1.0, "Patient discharged successfully", NOW);
        store.recordOutcome(improvingCaseId, TENANT, successOutcome);

        // Record a partial outcome for the worsening case
        var partialOutcome = CbrOutcome.of(0.4,
            "Initial treatment failed, required ICU escalation", NOW);
        store.recordOutcome(worseningCaseId, TENANT, partialOutcome);

        // Demonstrate EMA confidence math directly
        var initialConf = new Confidence(ConfidenceOrigin.STATED, 0.8, NOW);
        Confidence afterSuccess = CbrOutcome.adjustConfidence(initialConf, 1.0, CbrOutcome.DEFAULT_LEARNING_RATE);
        Confidence afterFailure = CbrOutcome.adjustConfidence(initialConf, 0.0, CbrOutcome.DEFAULT_LEARNING_RATE);

        // Success pushes confidence up: 0.8 * 0.8 + 0.2 * 1.0 = 0.84
        assertThat(afterSuccess.value()).isCloseTo(0.84, within(0.001));
        // Failure pulls confidence down: 0.8 * 0.8 + 0.2 * 0.0 = 0.64
        assertThat(afterFailure.value()).isCloseTo(0.64, within(0.001));

        System.out.println("\n=== Phase 6: Outcome Feedback ===");
        System.out.printf("  EMA (success): %.2f → %.2f%n", initialConf.value(), afterSuccess.value());
        System.out.printf("  EMA (failure): %.2f → %.2f%n", initialConf.value(), afterFailure.value());
    }

    // ─── Phase 7: Supersession ───────────────────────────────────────────

    @Test @Order(7)
    void supersessionExcludesCasesFromRetrieval() {
        // Store a revised version of the worsening case (updated treatment protocol)
        String revisedCaseId = storeCase(
            "Chest pain in 72yo — revised protocol: early catheterisation",
            "Immediate catheterisation instead of beta-blocker trial. Better outcome.",
            features(
                "age", FeatureValue.number(72),
                "diagnosis", FeatureValue.string("acute-coronary-syndrome"),
                "ward", FeatureValue.string("cardiology"),
                "severity", FeatureValue.number(9),
                "heartRate", hrSeries(
                    new double[]{0, 95}, new double[]{1, 100}, new double[]{2, 105}),
                "treatmentSteps", FeatureValue.stringList("assess", "ecg", "catheterisation", "stent")),
            NOW);

        // Supersede the old case
        boolean superseded = store.supersede(
            worseningCaseId, TENANT, revisedCaseId, "Updated to early catheterisation protocol");

        assertThat(superseded).isTrue();

        SupersessionStatus status = store.getSupersessionStatus(worseningCaseId, TENANT);
        assertThat(status.superseded()).isTrue();
        assertThat(status.reason()).contains("catheterisation");

        // Retrieve — superseded case should be excluded
        var queryFeatures = features(
            "age", FeatureValue.number(70),
            "diagnosis", FeatureValue.string("acute-coronary-syndrome"),
            "ward", FeatureValue.string("cardiology"),
            "severity", FeatureValue.number(8),
            "heartRate", hrSeries(new double[]{0, 98}, new double[]{1, 105}, new double[]{2, 112}),
            "treatmentSteps", FeatureValue.stringList("assess", "ecg"));

        var results = store.retrieveSimilar(
            CbrQuery.of(TENANT, DOMAIN, SCOPE, CASE_TYPE, queryFeatures, 10)
                .withRetrievalMode(RetrievalMode.FEATURE_ONLY),
            FeatureVectorCbrCase.class);

        assertThat(results.stream().map(ScoredCbrCase::caseId))
            .doesNotContain(worseningCaseId)
            .contains(revisedCaseId);

        // Reinstate the old case
        boolean reinstated = store.reinstate(worseningCaseId, TENANT);
        assertThat(reinstated).isTrue();
        assertThat(store.getSupersessionStatus(worseningCaseId, TENANT).wasReinstated()).isTrue();

        System.out.println("\n=== Phase 7: Supersession ===");
        System.out.printf("  Superseded: %s → %s%n", worseningCaseId, revisedCaseId);
        System.out.printf("  Reason: %s%n", status.reason());
        System.out.printf("  Reinstated: %s%n", reinstated);
    }

    // ─── Phase 8: Edit distance on treatment sequences ───────────────────

    @Test @Order(8)
    void editDistanceComparessTreatmentStepSequences() {
        // Directly compare two treatment step sequences
        var protocolA = List.of("assess", "ecg", "beta-blocker", "monitor", "discharge");
        var protocolB = List.of("assess", "ecg", "calcium-blocker", "icu-transfer", "angioplasty");

        // Without weighted substitutions — beta-blocker ↔ calcium-blocker costs 1.0
        EditDistanceResult unweighted = EditDistanceSimilarity.compute(protocolA, protocolB);

        // With weighted substitutions — beta-blocker ↔ calcium-blocker costs 0.3
        // (similarity 0.7 → cost 1.0 - 0.7 = 0.3) because they're in the same drug class
        EditDistanceResult weighted = EditDistanceSimilarity.compute(protocolA, protocolB,
            Map.of("beta-blocker", Map.of("calcium-blocker", 0.7)));

        // Weighted similarity is higher — swapping similar drugs costs less
        assertThat(weighted.score()).isGreaterThan(unweighted.score());

        // Show the alignment path
        System.out.println("\n=== Phase 8: Edit Distance on Treatment Steps ===");
        System.out.printf("  Protocol A: %s%n", protocolA);
        System.out.printf("  Protocol B: %s%n", protocolB);
        System.out.printf("  Unweighted similarity: %.3f%n", unweighted.score());
        System.out.printf("  Weighted similarity:   %.3f (beta/calcium-blocker = 0.7)%n", weighted.score());
        System.out.println("  Alignment:");
        for (var step : weighted.alignment()) {
            String qLabel = step.queryIndex() >= 0 ? protocolA.get(step.queryIndex()) : "—";
            String cLabel = step.caseIndex() >= 0 ? protocolB.get(step.caseIndex()) : "—";
            System.out.printf("    %s  %s ↔ %s%n", step.operation(), qLabel, cLabel);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private String storeCase(String problem, String solution,
                             Map<String, FeatureValue> features, Instant storedAt) {
        String caseId = UUID.randomUUID().toString();
        var cbrCase = new FeatureVectorCbrCase(
            problem, solution, null,
            new Confidence(ConfidenceOrigin.STATED, 0.8, storedAt),
            features, null, null);
        store.store(cbrCase, CASE_TYPE, UUID.randomUUID().toString(),
            DOMAIN, TENANT, caseId, SCOPE);
        return caseId;
    }

    private static Map<String, FeatureValue> features(Object... kvs) {
        Map<String, FeatureValue> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((String) kvs[i], (FeatureValue) kvs[i + 1]);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static FeatureValue.StructListVal hrSeries(double[]... points) {
        List<Map<String, FeatureValue>> items = new java.util.ArrayList<>();
        for (double[] p : points) {
            Map<String, FeatureValue> m = new java.util.LinkedHashMap<>();
            m.put("timestamp", FeatureValue.number(p[0]));
            m.put("bpm", FeatureValue.number(p[1]));
            items.add(m);
        }
        return FeatureValue.structList(items);
    }

    private double findScore(List<ScoredCbrCase<FeatureVectorCbrCase>> results, String caseId) {
        return results.stream()
            .filter(r -> r.caseId().equals(caseId))
            .mapToDouble(ScoredCbrCase::score)
            .findFirst()
            .orElse(0.0);
    }
}
