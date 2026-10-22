package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BayesModelProxyTest {

    interface DiagnosisModel {
        @Variable
        double cancer();

        @Variable(name = "Smoking")
        double smokingProbability();

        @Variable(name = "cancer", outcomeIndex = 0)
        double cancerFalse();

        @Evidence
        void setSmoking(String state);

        @Evidence(name = "Tar")
        void setTarDeposits(String state);

        boolean isDirty(String variable);

        void update();

        double[] distribution(String variable);
    }

    interface SimpleModel {
        @Variable
        double rain();

        @Evidence
        void setRain(String state);

        boolean isDirty(String variable);
    }

    private BayesInstance<?> buildDiagnosisInstance() {
        return BayesNetworkBuilder.create()
                .variable("Smoking", "false", "true")
                    .prior(0.3, 0.7)
                .variable("Tar", "false", "true")
                    .given("Smoking")
                    .cpt(0.95, 0.05,
                         0.1, 0.9)
                .variable("cancer", "false", "true")
                    .given("Smoking", "Tar")
                    .cpt(0.99, 0.01,
                         0.90, 0.10,
                         0.95, 0.05,
                         0.30, 0.70)
                .buildInstance();
    }

    @Test
    void typedGetterReturnsCorrectMarginal() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        // cancer() should return P(cancer=true) — last outcome by default
        double pCancer = model.cancer();

        // Manually verify
        instance.globalUpdate();
        double expected = instance.marginalize("cancer").getDistribution()[1];
        assertEquals(expected, pCancer, 1e-9);
    }

    @Test
    void explicitVariableNameMapping() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        // smokingProbability() maps to variable "Smoking" via @Variable(name="Smoking")
        double pSmoking = model.smokingProbability();
        assertEquals(0.7, pSmoking, 0.01);
    }

    @Test
    void explicitOutcomeIndex() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        // cancerFalse() returns P(cancer=false) via outcomeIndex=0
        double pCancerFalse = model.cancerFalse();
        double pCancerTrue = model.cancer();

        assertEquals(1.0, pCancerFalse + pCancerTrue, 0.001);
    }

    @Test
    void evidenceSetterDelegatesToInstance() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        double pCancerBefore = model.cancer();

        // Setting smoking=true should increase cancer probability
        model.setSmoking("true");
        double pCancerAfter = model.cancer();

        assertTrue(pCancerAfter > pCancerBefore,
                "Setting smoking=true should increase cancer probability");
    }

    @Test
    void evidenceSetterWithExplicitName() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        // setTarDeposits() maps to variable "Tar" via @Evidence(name="Tar")
        model.setTarDeposits("true");
        double pCancer = model.cancer();

        // Tar=true should increase cancer probability
        assertTrue(pCancer > 0.1);
    }

    @Test
    void dirtyBitSetWhenPosteriorChanges() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        // Initial state: no evidence set, first call propagates
        model.cancer(); // trigger initial propagation
        assertFalse(model.isDirty("cancer"), "Should not be dirty after initial read");

        // Set evidence — this changes posteriors
        model.setSmoking("true");
        model.update();
        assertTrue(model.isDirty("cancer"), "Should be dirty after evidence changes posterior");
    }

    @Test
    void dirtyBitClearWhenPosteriorUnchanged() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        model.cancer(); // initial propagation
        model.update(); // re-propagate with no evidence change
        assertFalse(model.isDirty("cancer"), "Should not be dirty when nothing changed");
    }

    @Test
    void dirtyBitResetsOnRead() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        model.cancer(); // initial
        model.setSmoking("true");
        model.update();

        assertTrue(model.isDirty("cancer"), "First isDirty should return true");
        assertFalse(model.isDirty("cancer"), "Second isDirty should return false (reset-on-read)");
    }

    @Test
    void distributionReturnsFullArray() {
        BayesInstance<?> instance = buildDiagnosisInstance();
        DiagnosisModel model = BayesModelProxy.create(DiagnosisModel.class, instance);

        double[] dist = model.distribution("cancer");
        assertEquals(2, dist.length);
        assertEquals(1.0, dist[0] + dist[1], 0.001);
    }

    @Test
    void worksWithBuilderNetworks() {
        BayesInstance<?> instance = BayesNetworkBuilder.create()
                .variable("rain", "no", "yes").prior(0.3, 0.7)
                .buildInstance();

        SimpleModel model = BayesModelProxy.create(SimpleModel.class, instance);

        double pRain = model.rain();
        assertEquals(0.7, pRain, 0.01);

        model.setRain("no");
        pRain = model.rain();
        // setRain("no") sets hard evidence rain=no, so P(rain=yes) = 0.0
        assertEquals(0.0, pRain, 0.01);
    }

    @Test
    void conventionBasedSetterNameDerivation() {
        // setSmoking → strips "set", lowercases first char → "smoking"
        // but variable name is "rain" → setRain → "rain" (lowercase first char)
        BayesInstance<?> instance = BayesNetworkBuilder.create()
                .variable("rain", "no", "yes").prior(0.3, 0.7)
                .buildInstance();

        SimpleModel model = BayesModelProxy.create(SimpleModel.class, instance);
        model.setRain("yes");
        assertEquals(1.0, model.rain(), 0.01);
    }
}
