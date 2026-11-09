package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.index.CognitiveDerivationEngine;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.RegulationDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #829: validate predictor fires for coherent characters, falls through for mixed.
 */
class PredictorScenarioValidationTest {

    private final RegulationStrategyPredictor predictor = new RegulationStrategyPredictor();

    // Wacky-manor BASELINE character dispositions (from descriptors-baseline.yaml)
    record CharacterProfile(String name, DispositionAxes axes, RegulationDefaults rd) {
        CharacterProfile(String name, DispositionAxes axes) {
            this(name, axes, CognitiveDerivationEngine.deriveRegulationDefaults(axes));
        }
    }

    static final List<CharacterProfile> CHARACTERS = List.of(
            new CharacterProfile("penelope-pitstop",
                    new DispositionAxes("cooperative", "strict", "moderate", "low", "cooperative")),
            new CharacterProfile("hooded-claw",
                    new DispositionAxes("competitive", "flexible", "bold", "high", "competitive")),
            new CharacterProfile("ant-hill-mob",
                    new DispositionAxes("cooperative", "flexible", "bold", "low", "cooperative")),
            new CharacterProfile("dick-dastardly",
                    new DispositionAxes("competitive", "flexible", "bold", "high", "competitive")),
            new CharacterProfile("peter-perfect",
                    new DispositionAxes("cooperative", "strict", "bold", "low", "cooperative"))
    );

    static final EmotionType[] REGULATION_EMOTIONS = {
            EmotionType.ANGER, EmotionType.FEAR, EmotionType.SHAME, EmotionType.REPROACH
    };

    @Test
    void printCapacityMatrix() {
        System.out.println("\n=== Derived Regulation Capacities ===");
        System.out.printf("%-20s  %6s  %6s  %6s  axes%n",
                "Character", "reappr", "suppr", "redir");
        System.out.println("-".repeat(75));
        for (var c : CHARACTERS) {
            System.out.printf("%-20s  %6.2f  %6.2f  %6.2f  %s/%s/%s/%s/%s%n",
                    c.name(), c.rd().reappraisalCapacity(), c.rd().suppressionCapacity(),
                    c.rd().redirectTendency(),
                    c.axes().socialOrient(), c.axes().ruleFollowing(),
                    c.axes().riskAppetite(), c.axes().autonomy(), c.axes().conflictMode());
        }
    }

    @Test
    void printPredictionMatrix() {
        System.out.println("\n=== Predictor Decision Matrix (intensity=0.5, fatigue=0.0) ===");
        System.out.printf("%-20s", "Character");
        for (var e : REGULATION_EMOTIONS) {
            System.out.printf("  %-18s", e.name());
        }
        System.out.println();
        System.out.println("-".repeat(100));

        int totalCells = 0;
        int pinned = 0;
        for (var c : CHARACTERS) {
            System.out.printf("%-20s", c.name());
            for (var emotion : REGULATION_EMOTIONS) {
                var result = predictor.predict(c.rd(), c.axes(), emotion, 0.5, 0.0, null);
                totalCells++;
                if (result.isPresent()) {
                    pinned++;
                    System.out.printf("  %-18s", result.get().strategy().name());
                } else {
                    System.out.printf("  %-18s", "— (fall-through)");
                }
            }
            System.out.println();
        }
        System.out.println("-".repeat(100));
        System.out.printf("Prediction rate: %d/%d (%.0f%% of LLM strategy calls saved)%n",
                pinned, totalCells, (100.0 * pinned / totalCells));
    }

    @Test
    void fearAlwaysPinsAttentionalDeployment() {
        for (var c : CHARACTERS) {
            var result = predictor.predict(c.rd(), c.axes(), EmotionType.FEAR, 0.5, 0.0, null);
            assertThat(result)
                    .as("FEAR should always pin AD for %s", c.name())
                    .isPresent()
                    .hasValueSatisfying(p ->
                            assertThat(p.strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT));
        }
    }

    @Test
    void highFatigueOverridesAllCharacters() {
        for (var c : CHARACTERS) {
            var result = predictor.predict(c.rd(), c.axes(), EmotionType.ANGER, 0.5, 0.8, null);
            assertThat(result)
                    .as("High fatigue should override for %s", c.name())
                    .isPresent()
                    .hasValueSatisfying(p ->
                            assertThat(p.strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT));
        }
    }

    @Test
    void highIntensityOverridesAllCharacters() {
        for (var c : CHARACTERS) {
            var result = predictor.predict(c.rd(), c.axes(), EmotionType.ANGER, 0.9, 0.0, null);
            assertThat(result)
                    .as("High intensity should override for %s", c.name())
                    .isPresent()
                    .hasValueSatisfying(p ->
                            assertThat(p.strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT));
        }
    }

    @Test
    void identicalProfilesProduceIdenticalPredictions() {
        // hooded-claw and dick-dastardly share the same disposition axes
        var hoodedClaw = CHARACTERS.stream().filter(c -> c.name().equals("hooded-claw")).findFirst().orElseThrow();
        var dickDastardly = CHARACTERS.stream().filter(c -> c.name().equals("dick-dastardly")).findFirst().orElseThrow();

        for (var emotion : REGULATION_EMOTIONS) {
            var hcResult = predictor.predict(hoodedClaw.rd(), hoodedClaw.axes(), emotion, 0.5, 0.0, null);
            var ddResult = predictor.predict(dickDastardly.rd(), dickDastardly.axes(), emotion, 0.5, 0.0, null);
            assertThat(hcResult)
                    .as("Identical profiles should predict identically for %s", emotion)
                    .isEqualTo(ddResult);
        }
    }

    @Test
    void atLeastOneCharacterPinsForControllableNegative() {
        boolean anyPinned = CHARACTERS.stream().anyMatch(c -> {
            var result = predictor.predict(c.rd(), c.axes(), EmotionType.ANGER, 0.5, 0.0, null);
            return result.isPresent();
        });
        assertThat(anyPinned)
                .as("At least one character should pin a strategy for ANGER")
                .isTrue();
    }

    @Test
    void positiveEmotionsNeverPin() {
        for (var c : CHARACTERS) {
            var result = predictor.predict(c.rd(), c.axes(), EmotionType.JOY, 0.5, 0.0, null);
            assertThat(result)
                    .as("JOY should never pin for %s", c.name())
                    .isEmpty();
        }
    }

    @Test
    void predictionRateIsReasonable() {
        int totalCells = 0;
        int pinned = 0;
        for (var c : CHARACTERS) {
            for (var emotion : REGULATION_EMOTIONS) {
                var result = predictor.predict(c.rd(), c.axes(), emotion, 0.5, 0.0, null);
                totalCells++;
                if (result.isPresent()) pinned++;
            }
        }
        double rate = (double) pinned / totalCells;
        // Current BASELINE cast: 95% — all 5 characters are strong reappraisers.
        // Only penelope-pitstop falls through for ANGER (narrow CC/RM gap = 0.25 < 0.30).
        assertThat(rate)
                .as("Prediction rate should be at least 25%% — validates predictor is contributing")
                .isGreaterThanOrEqualTo(0.25);
    }
}
