package io.quarkmind.perception;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.BayesVariable;
import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import io.quarkmind.inference.bayes.graph.GraphNode;
import io.quarkmind.inference.bayes.graph.impl.EdgeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Refs #630 — dynamic Bayes net: temporal evidence across ticks.
 */
public class TemporalEvidenceFusionEngineTest {

    private BayesNetwork graph;
    private TemporalEvidenceFusionEngine engine;

    @BeforeEach
    void setUp() {
        graph = buildChainNetwork(8);
        engine = new TemporalEvidenceFusionEngine();
    }

    // ── Core temporal behavior ──────────────────────────────────────────────

    @Test
    void temporalPrior_influencesNextTickPosterior() {
        engine.initialize(graph, BayesNetworkFixtures.varName(7), 0.8);

        // Tick 1: strong evidence toward "true"
        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.9});
        engine.update();
        double[] tick1Posterior = engine.marginalize().clone();

        // Tick 2: weak contradictory evidence toward "false"
        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        engine.update();
        double[] tick2Posterior = engine.marginalize();

        // Without temporal prior, tick2 would flip to favoring "false".
        // With temporal prior (decay=0.8), tick1's "true" bias should persist,
        // making tick2's "true" probability higher than a fresh start would give.
        TemporalEvidenceFusionEngine fresh = new TemporalEvidenceFusionEngine();
        fresh.initialize(graph, BayesNetworkFixtures.varName(7), 0.0);
        fresh.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        fresh.update();
        double[] freshPosterior = fresh.marginalize();

        assertThat(tick2Posterior[1])
                .as("temporal smoothing should keep 'true' higher than fresh")
                .isGreaterThan(freshPosterior[1]);
    }

    @Test
    void evidenceDecaysOverMultipleTimeSteps() {
        engine.initialize(graph, BayesNetworkFixtures.varName(7), 0.5);

        // Tick 1: strong evidence
        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.9});
        engine.update();
        double tick1True = engine.marginalize()[1];

        // Ticks 2-5: no additional evidence (only temporal prior carries)
        for (int tick = 2; tick <= 5; tick++) {
            engine.setAllEvidence(Map.of());
            engine.update();
        }
        double tick5True = engine.marginalize()[1];

        // With decay=0.5, the bias should weaken over time toward uniform
        assertThat(tick5True).isLessThan(tick1True);
        assertThat(tick5True).isCloseTo(0.5, within(0.1));
    }

    @Test
    void statePersistsAcrossAdvanceTimeSlice() {
        engine.initialize(graph, BayesNetworkFixtures.varName(7), 0.9);

        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.2, 0.8});
        engine.update();
        assertThat(engine.getTickCount()).isEqualTo(1);
        assertThat(engine.getPreviousPosterior()).isNotNull();

        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.3, 0.7});
        engine.update();
        assertThat(engine.getTickCount()).isEqualTo(2);
        assertThat(engine.getPreviousPosterior()).isNotNull();
    }

    // ── Decay factor behavior ───────────────────────────────────────────────

    @Test
    void zeroDecay_behavesLikeNonTemporalEngine() {
        engine.initialize(graph, BayesNetworkFixtures.varName(7), 0.0);

        EvidenceFusionEngine nonTemporal = new EvidenceFusionEngine();
        nonTemporal.initialize(graph, BayesNetworkFixtures.varName(7));

        // Tick 1
        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.9});
        nonTemporal.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.9});
        engine.update();
        nonTemporal.update();

        // Tick 2
        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        nonTemporal.reset();
        nonTemporal.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        engine.update();
        nonTemporal.update();

        double[] temporal = engine.marginalize();
        double[] plain = nonTemporal.marginalize();

        for (int i = 0; i < temporal.length; i++) {
            assertThat(temporal[i]).as("state[%d]", i).isCloseTo(plain[i], within(1e-10));
        }
    }

    @Test
    void higherDecay_producesStrongerTemporalInfluence() {
        TemporalEvidenceFusionEngine lowDecay = new TemporalEvidenceFusionEngine();
        TemporalEvidenceFusionEngine highDecay = new TemporalEvidenceFusionEngine();

        lowDecay.initialize(graph, BayesNetworkFixtures.varName(7), 0.3);
        highDecay.initialize(graph, BayesNetworkFixtures.varName(7), 0.9);

        // Tick 1: both see strong "true"
        lowDecay.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.9});
        highDecay.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.9});
        lowDecay.update();
        highDecay.update();

        // Tick 2: both see weak "false"
        lowDecay.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.7, 0.3});
        highDecay.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.7, 0.3});
        lowDecay.update();
        highDecay.update();

        // High decay should retain more of tick1's "true" bias
        assertThat(highDecay.marginalize()[1])
                .isGreaterThan(lowDecay.marginalize()[1]);
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Test
    void reset_clearsTemporalState() {
        engine.initialize(graph, BayesNetworkFixtures.varName(7), 0.8);

        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.9});
        engine.update();
        assertThat(engine.getPreviousPosterior()).isNotNull();

        engine.reset();
        assertThat(engine.getPreviousPosterior()).isNull();
        assertThat(engine.getTickCount()).isEqualTo(0);
    }

    @Test
    void invalidDecay_throwsIllegalArgument() {
        assertThatThrownBy(() -> engine.initialize(graph, BayesNetworkFixtures.varName(7), 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.initialize(graph, BayesNetworkFixtures.varName(7), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setTemporalDecay_midSession_takesEffectImmediately() {
        engine.initialize(graph, BayesNetworkFixtures.varName(7), 0.9);

        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.9});
        engine.update();

        engine.setTemporalDecay(0.0);
        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        engine.update();

        // With decay now 0, temporal prior should have no effect
        EvidenceFusionEngine fresh = new EvidenceFusionEngine();
        fresh.initialize(graph, BayesNetworkFixtures.varName(7));
        fresh.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        fresh.update();
        double[] freshDist = fresh.marginalize();
        double[] temporalDist = engine.marginalize();

        for (int i = 0; i < temporalDist.length; i++) {
            assertThat(temporalDist[i]).as("state[%d]", i).isCloseTo(freshDist[i], within(1e-10));
        }
    }

    @Test
    void blendWithUniform_fullDecay_returnsOriginal() {
        double[] dist = {0.8, 0.2};
        double[] result = TemporalEvidenceFusionEngine.blendWithUniform(dist, 1.0);
        assertThat(result[0]).isCloseTo(0.8, within(1e-15));
        assertThat(result[1]).isCloseTo(0.2, within(1e-15));
    }

    @Test
    void blendWithUniform_noDecay_returnsUniform() {
        double[] dist = {0.8, 0.2};
        double[] result = TemporalEvidenceFusionEngine.blendWithUniform(dist, 0.0);
        assertThat(result[0]).isCloseTo(0.5, within(1e-15));
        assertThat(result[1]).isCloseTo(0.5, within(1e-15));
    }

    @Test
    void blendWithUniform_halfDecay_blends() {
        double[] dist = {0.8, 0.2};
        double[] result = TemporalEvidenceFusionEngine.blendWithUniform(dist, 0.5);
        assertThat(result[0]).isCloseTo(0.65, within(1e-15));
        assertThat(result[1]).isCloseTo(0.35, within(1e-15));
    }

    @Test
    void hardEvidence_worksWithTemporalEngine() {
        engine.initialize(graph, BayesNetworkFixtures.varName(7), 0.8);

        engine.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        engine.update();

        double[] dist = engine.marginalize();
        assertThat(dist).isNotNull();
        for (double v : dist) {
            assertThat(v).isNotNaN();
        }
    }

    @Test
    void batchEvidence_worksWithTemporalEngine() {
        engine.initialize(graph, BayesNetworkFixtures.varName(7), 0.8);

        engine.setAllEvidence(Map.of(
                BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2},
                BayesNetworkFixtures.varName(4), new double[]{0.6, 0.4}
        ));
        engine.update();

        assertThat(engine.getTickCount()).isEqualTo(1);
        double[] dist = engine.marginalize();
        for (double v : dist) {
            assertThat(v).isNotNaN();
        }
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static BayesNetwork buildChainNetwork(int n) {
        BayesNetwork network = new BayesNetwork();
        GraphNode<BayesVariable>[] nodes = new GraphNode[n];
        for (int i = 0; i < n; i++) nodes[i] = network.addNode();

        nodes[0].setContent(new BayesVariable<>(BayesNetworkFixtures.varName(0), nodes[0].getId(),
                new String[]{"false", "true"}, new double[][]{{0.5, 0.5}}));

        for (int i = 1; i < n; i++) {
            nodes[i].setContent(new BayesVariable<>(BayesNetworkFixtures.varName(i), nodes[i].getId(),
                    new String[]{"false", "true"}, new double[][]{{0.7, 0.3}, {0.4, 0.6}}));
            EdgeImpl edge = new EdgeImpl();
            edge.setOutGraphNode(nodes[i - 1]);
            edge.setInGraphNode(nodes[i]);
        }
        return network;
    }
}
