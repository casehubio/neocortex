package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class ExplanationTraceTest {

    // --- BayesInstance accessor tests ---

    @Test
    void getJunctionTree_returnsTheTree() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);
        assertThat(instance.getJunctionTree()).isSameAs(jt);
    }

    @Test
    void getEvidenceVariables_returnsSetEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("var_0", new double[]{0.9, 0.1});
        instance.setLikelyhood("var_2", new double[]{0.6, 0.4});
        instance.globalUpdate();

        Map<String, double[]> evidence = instance.getEvidenceVariables();
        assertThat(evidence).containsOnlyKeys("var_0", "var_2");
        assertThat(evidence.get("var_0")).containsExactly(0.9, 0.1);
    }

    @Test
    void getEvidenceVariables_emptyWhenNoEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);
        assertThat(instance.getEvidenceVariables()).isEmpty();
    }

    // --- ExplanationEngine tests ---

    @Test
    void explain_ranksInfluenceByMagnitude() {
        BayesNetwork network = BayesNetworkBuilder.create()
            .variable("A", "false", "true").prior(0.5, 0.5)
            .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
            .variable("C", "false", "true").given("B").cpt(0.8, 0.2, 0.3, 0.7)
            .variable("D", "false", "true").given("C").cpt(0.9, 0.1, 0.2, 0.8)
            .build();
        JunctionTree jt = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.9, 0.1});
        instance.setLikelyhood("C", new double[]{0.9, 0.1});
        instance.globalUpdate();

        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "D");

        assertThat(trace.influences()).hasSize(2);
        assertThat(trace.influences().get(0).variableName()).isEqualTo("C");
        assertThat(trace.influences().get(1).variableName()).isEqualTo("A");
        assertThat(trace.influences().get(0).influence())
            .isGreaterThan(trace.influences().get(1).influence());
    }

    @Test
    void explain_influenceMatchesSensitivity() {
        BayesNetwork network = BayesNetworkBuilder.create()
            .variable("A", "false", "true").prior(0.5, 0.5)
            .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
            .variable("C", "false", "true").given("B").cpt(0.8, 0.2, 0.3, 0.7)
            .build();
        JunctionTree jt = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.9, 0.1});
        instance.globalUpdate();

        double sensitivity = instance.sensitivity("A", "C");
        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "C");

        assertThat(trace.influences()).hasSize(1);
        assertThat(trace.influences().get(0).influence())
            .isCloseTo(sensitivity, within(1e-10));
    }

    @Test
    void explain_detectsDirection() {
        BayesNetwork network = BayesNetworkBuilder.create()
            .variable("A", "false", "true").prior(0.5, 0.5)
            .variable("B", "false", "true").given("A").cpt(0.9, 0.1, 0.1, 0.9)
            .build();
        JunctionTree jt = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.0, 1.0});
        instance.globalUpdate();

        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "B");

        assertThat(trace.influences().get(0).direction()).isEqualTo(Direction.INCREASED);
    }

    @Test
    void explain_priorAndPosteriorDistributions() {
        BayesNetwork network = BayesNetworkBuilder.create()
            .variable("A", "false", "true").prior(0.5, 0.5)
            .variable("B", "false", "true").given("A").cpt(0.8, 0.2, 0.3, 0.7)
            .build();
        JunctionTree jt = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.0, 1.0});
        instance.globalUpdate();

        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "B");

        assertThat(trace.priorDistribution()[0]).isCloseTo(0.55, within(0.01));
        assertThat(trace.priorDistribution()[1]).isCloseTo(0.45, within(0.01));
        assertThat(trace.posteriorDistribution()[0]).isCloseTo(0.3, within(0.01));
        assertThat(trace.posteriorDistribution()[1]).isCloseTo(0.7, within(0.01));
        assertThat(trace.targetOutcomes()).containsExactly("false", "true");
    }

    @Test
    void explain_noEvidence_throwsIllegalArgument() {
        BayesNetwork network = BayesNetworkBuilder.create()
            .variable("A", "false", "true").prior(0.5, 0.5)
            .variable("B", "false", "true").given("A").cpt(0.8, 0.2, 0.3, 0.7)
            .build();
        JunctionTree jt = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> ExplanationEngine.explain(network, instance, "B"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explain_doesNotMutateInstance() {
        BayesNetwork network = BayesNetworkBuilder.create()
            .variable("A", "false", "true").prior(0.5, 0.5)
            .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
            .variable("C", "false", "true").given("B").cpt(0.8, 0.2, 0.3, 0.7)
            .build();
        JunctionTree jt = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.9, 0.1});
        instance.globalUpdate();

        double[] beforeDist = instance.marginalize("C").getDistribution().clone();

        ExplanationEngine.explain(network, instance, "C");

        double[] afterDist = instance.marginalize("C").getDistribution();
        for (int i = 0; i < beforeDist.length; i++) {
            assertThat(afterDist[i]).isCloseTo(beforeDist[i], within(1e-10));
        }
    }

// --- Message path tests ---

    @Test
    void explain_messagePathInChain() {
        BayesNetwork network = BayesNetworkBuilder.create()
                                                  .variable("A", "false", "true").prior(0.5, 0.5)
                                                  .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                                  .variable("C", "false", "true").given("B").cpt(0.8, 0.2, 0.3, 0.7)
                                                  .variable("D", "false", "true").given("C").cpt(0.9, 0.1, 0.2, 0.8)
                                                  .build();
        JunctionTree     jt       = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.9, 0.1});
        instance.globalUpdate();

        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "D");

        List<String> path = trace.influences().get(0).messagePath();
        assertThat(path.get(0)).contains("A");
        assertThat(path.get(path.size() - 1)).contains("D");
        assertThat(path.size()).isGreaterThan(2);
    }

    @Test
    void explain_messagePathSameClique() {
        BayesNetwork network = BayesNetworkBuilder.create()
                                                  .variable("A", "false", "true").prior(0.5, 0.5)
                                                  .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                                  .build();
        JunctionTree     jt       = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.9, 0.1});
        instance.globalUpdate();

        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "B");

        List<String> path = trace.influences().get(0).messagePath();
        assertThat(path).isNotEmpty();
        assertThat(path.get(0)).contains("A");
        assertThat(path.get(0)).contains("B");
    }

// --- Confounding tests ---

    @Test
    void explain_detectsConfounding() {
        // A→B→C with A→C as confounder. Observing B and querying C:
        // P(C|B) ≠ P(C|do(B)) because A confounds the B→C relationship.
        // Intervening on B severs A→B but B→C survives — graph stays connected.
        BayesNetwork network = BayesNetworkBuilder.create()
                                                  .variable("A", "false", "true").prior(0.5, 0.5)
                                                  .variable("B", "false", "true").given("A").cpt(0.9, 0.1, 0.1, 0.9)
                                                  .variable("C", "false", "true").given("A", "B")
                                                  .cpt(0.9, 0.1, 0.7, 0.3, 0.6, 0.4, 0.1, 0.9)
                                                  .build();
        JunctionTree     jt       = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("B", new double[]{0.0, 1.0});
        instance.globalUpdate();

        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "C");

        assertThat(trace.influences()).hasSize(1);
        EvidenceInfluence bInfluence = trace.influences().get(0);
        assertThat(bInfluence.variableName()).isEqualTo("B");
        assertThat(bInfluence.confounded()).isTrue();
    }

    @Test
    void explain_noConfoundingInChain() {
        BayesNetwork network = BayesNetworkBuilder.create()
                                                  .variable("A", "false", "true").prior(0.5, 0.5)
                                                  .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                                  .variable("C", "false", "true").given("B").cpt(0.8, 0.2, 0.3, 0.7)
                                                  .build();
        JunctionTree     jt       = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.0, 1.0});
        instance.globalUpdate();

        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "C");

        assertThat(trace.influences()).hasSize(1);
        assertThat(trace.influences().get(0).confounded()).isFalse();
    }

// --- End-to-end test ---

    @Test
    void explain_worksWithBayesNetworkBuilder() {
        // Smoking→XRay→Cancer with Smoking→Cancer as confounder.
        // Intervening on XRay severs Smoking→XRay but XRay→Cancer survives.
        BayesNetwork network = BayesNetworkBuilder.create("diagnosis")
                                                  .variable("Smoking", "false", "true").prior(0.3, 0.7)
                                                  .variable("XRay", "false", "true")
                                                  .given("Smoking").cpt(0.8, 0.2, 0.3, 0.7)
                                                  .variable("Cancer", "false", "true")
                                                  .given("Smoking", "XRay")
                                                  .cpt(0.95, 0.05, 0.85, 0.15, 0.7, 0.3, 0.4, 0.6)
                                                  .build();
        JunctionTree     jt       = new JunctionTreeBuilder(network).build();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("XRay", new double[]{0.0, 1.0});
        instance.globalUpdate();

        ExplanationTrace trace = ExplanationEngine.explain(network, instance, "Cancer");

        assertThat(trace.targetVar()).isEqualTo("Cancer");
        assertThat(trace.influences()).hasSize(1);
        assertThat(trace.influences().get(0).variableName()).isEqualTo("XRay");
        assertThat(trace.influences().get(0).influence()).isGreaterThan(0.0);
        assertThat(trace.influences().get(0).confounded()).isTrue();
    }
}
