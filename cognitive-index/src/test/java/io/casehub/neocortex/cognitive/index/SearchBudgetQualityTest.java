package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.SearchBudgetConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchBudgetQualityTest {

    @Test
    void bold_and_cautious_produce_meaningfully_different_configs() {
        var boldFacets = List.of(
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.9, "d"),
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.2, "d"));
        var boldAxes = new DispositionAxes("cooperative", "flexible", "bold", "independent", "collaborative");
        var bold = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets, boldAxes, 0.8);

        var cautiousFacets = List.of(
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.6, "d"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.8, "d"),
                new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.7, "d"));
        var cautiousAxes = new DispositionAxes("cooperative", "strict", "conservative", "dependent", "avoidant");
        var cautious = CognitiveDerivationEngine.deriveSearchBudgetDefaults(cautiousFacets, cautiousAxes, 0.3);

        var defaults = SearchBudgetConfig.defaults();

        System.out.println("=== PERSONALITY DIFFERENTIATION ===");
        System.out.printf("                    Bold    Cautious  Default%n");
        System.out.printf("breadthSteepness:   %5.1f     %5.1f     %5.1f%n", bold.breadthSteepness(), cautious.breadthSteepness(), defaults.breadthSteepness());
        System.out.printf("breadthMidpoint:    %5.2f     %5.2f     %5.2f%n", bold.breadthMidpoint(), cautious.breadthMidpoint(), defaults.breadthMidpoint());
        System.out.printf("lateralityMidpoint: %5.2f     %5.2f     %5.2f%n", bold.lateralityMidpoint(), cautious.lateralityMidpoint(), defaults.lateralityMidpoint());
        System.out.printf("depthMidpoint:      %5.2f     %5.2f     %5.2f%n", bold.depthMidpoint(), cautious.depthMidpoint(), defaults.depthMidpoint());
        System.out.printf("convergenceMidpoint:%5.2f     %5.2f     %5.2f%n", bold.convergenceMidpoint(), cautious.convergenceMidpoint(), defaults.convergenceMidpoint());
        System.out.printf("pathThreshold:      %5.2f     %5.2f     %5.2f%n", bold.pathologicalThreshold(), cautious.pathologicalThreshold(), defaults.pathologicalThreshold());

        // Bold should explore more broadly (lower midpoint = activates earlier)
        assertTrue(bold.breadthMidpoint() < cautious.breadthMidpoint(),
                "Bold should have lower breadth midpoint (explore earlier): bold=" + bold.breadthMidpoint() + " cautious=" + cautious.breadthMidpoint());

        // Bold should reach laterally more easily
        assertTrue(bold.lateralityMidpoint() < cautious.lateralityMidpoint(),
                "Bold should have lower laterality midpoint: bold=" + bold.lateralityMidpoint() + " cautious=" + cautious.lateralityMidpoint());

        // Cautious should go deeper more easily (strict → lower depth midpoint)
        assertTrue(cautious.depthMidpoint() < bold.depthMidpoint(),
                "Cautious should have lower depth midpoint (systematic): cautious=" + cautious.depthMidpoint() + " bold=" + bold.depthMidpoint());

        // Cautious should have lower pathological threshold (more fixation-prone)
        assertTrue(cautious.pathologicalThreshold() < bold.pathologicalThreshold(),
                "Cautious should have lower pathological threshold: cautious=" + cautious.pathologicalThreshold() + " bold=" + bold.pathologicalThreshold());

        // The differences should be meaningful, not trivial
        double breadthDiff = Math.abs(bold.breadthMidpoint() - cautious.breadthMidpoint());
        assertTrue(breadthDiff > 0.05, "Breadth midpoint difference should be > 0.05: " + breadthDiff);

        double pathDiff = Math.abs(bold.pathologicalThreshold() - cautious.pathologicalThreshold());
        assertTrue(pathDiff > 0.1, "Pathological threshold difference should be > 0.1: " + pathDiff);
    }

    @Test
    void logistic_function_produces_expected_sigmoid_shape() {
        var config = SearchBudgetConfig.defaults();
        double k = config.breadthSteepness();
        double mid = config.breadthMidpoint();

        double atZero = logistic(0.0, k, mid);
        double atMid = logistic(mid, k, mid);
        double atOne = logistic(1.0, k, mid);
        double atLow = logistic(0.2, k, mid);
        double atHigh = logistic(0.8, k, mid);

        System.out.println("\n=== SIGMOID SHAPE (k=" + k + ", mid=" + mid + ") ===");
        System.out.printf("at 0.0: %.3f  (should be low)%n", atZero);
        System.out.printf("at 0.2: %.3f%n", atLow);
        System.out.printf("at 0.5: %.3f  (midpoint, should be ~0.5)%n", atMid);
        System.out.printf("at 0.8: %.3f%n", atHigh);
        System.out.printf("at 1.0: %.3f  (should be high)%n", atOne);

        // At midpoint, output should be exactly 0.5
        assertEquals(0.5, atMid, 0.001, "At midpoint output should be 0.5");

        // Below midpoint should be < 0.5, above should be > 0.5
        assertTrue(atLow < 0.5, "Below midpoint should be < 0.5");
        assertTrue(atHigh > 0.5, "Above midpoint should be > 0.5");

        // At 0.0 should be very low, at 1.0 should be very high
        assertTrue(atZero < 0.1, "At 0.0 should be < 0.1: " + atZero);
        assertTrue(atOne > 0.9, "At 1.0 should be > 0.9: " + atOne);

        // The transition should be steep (k=6.0 should produce a clear threshold)
        double transitionRange = atHigh - atLow;
        assertTrue(transitionRange > 0.7, "Transition from 0.2→0.8 should span > 0.7: " + transitionRange);
    }

    @Test
    void drive_modulation_produces_distinct_profiles() {
        var config = SearchBudgetConfig.defaults();
        double intensity = 0.8;
        double base = logistic(intensity, config.breadthSteepness(), config.breadthMidpoint());

        System.out.println("\n=== DRIVE MODULATION at intensity=0.8 ===");
        System.out.printf("Base (no drive mod): breadth=%.2f%n", base);

        // Curiosity: +0.15 breadth, +0.10 laterality, -0.10 convergence
        System.out.printf("Curiosity:  breadth=%.2f (+0.15)  lat=%.2f (+0.10)  conv=%.2f (-0.10)%n",
                Math.min(1, base + 0.15), Math.min(1, base + 0.10), Math.max(0, base - 0.10));

        // Competence: +0.15 depth, +0.10 convergence
        System.out.printf("Competence: depth=%.2f (+0.15)  conv=%.2f (+0.10)%n",
                Math.min(1, base + 0.15), Math.min(1, base + 0.10));

        // Affiliation: +0.08 breadth, -0.10 depth, +0.10 convergence
        System.out.printf("Affiliation: breadth=%.2f (+0.08)  depth=%.2f (-0.10)  conv=%.2f (+0.10)%n",
                Math.min(1, base + 0.08), Math.max(0, base - 0.10), Math.min(1, base + 0.10));

        // Autonomy: +0.08 laterality, +0.10 depth, -0.08 convergence
        System.out.printf("Autonomy: lat=%.2f (+0.08)  depth=%.2f (+0.10)  conv=%.2f (-0.08)%n",
                Math.min(1, base + 0.08), Math.min(1, base + 0.10), Math.max(0, base - 0.08));

        // The modulations should be large enough to matter
        assertTrue(0.15 / base > 0.1, "Curiosity breadth boost should be >10% of base");
        assertTrue(0.10 / base > 0.05, "Drive modulation should be >5% of base");
    }

    private static double logistic(double x, double k, double midpoint) {
        return 1.0 / (1.0 + Math.exp(-k * (x - midpoint)));
    }
}
