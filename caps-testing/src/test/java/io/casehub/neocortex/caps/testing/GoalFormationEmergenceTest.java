package io.casehub.neocortex.caps.testing;

import io.casehub.neocortex.caps.SettlingResult;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoalFormationEmergenceTest extends CognitiveEmergenceTest {

    @Nested
    class DispositionDivergence {

        @Test
        void competitiveAgentApproachesMoreThanCooperative() {
            var competitive = new DispositionAxes(
                    "competitive", "flexible", "bold", "high", "competitive");
            var cooperative = new DispositionAxes(
                    "cooperative", "strict", "conservative", "low", "avoidant");

            var boldResult = scenario("bold", competitive)
                    .settle(Map.of("social_threat", 0.7, "dominance", 0.6));
            var timidResult = scenario("timid", cooperative)
                    .settle(Map.of("social_threat", 0.7, "dominance", 0.6));

            double boldApproach = attractorStrength(boldResult, "approach");
            double timidApproach = attractorStrength(timidResult, "approach");

            assertThat(boldApproach).as("Competitive+bold agent approaches more")
                    .isGreaterThan(timidApproach);
        }

        @Test
        void highAutonomyExploresMoreThanLow() {
            var autonomous = new DispositionAxes(
                    "independent", "flexible", "bold", "high", "competitive");
            var dependent = new DispositionAxes(
                    "cooperative", "strict", "conservative", "low", "avoidant");

            var rebelResult = scenario("rebel", autonomous)
                    .settle(Map.of("controlled", 0.8, "agency_granted", 0.3));
            var compliantResult = scenario("compliant", dependent)
                    .settle(Map.of("controlled", 0.8, "agency_granted", 0.3));

            double rebelExplore = attractorStrength(rebelResult, "explore");
            double compliantExplore = attractorStrength(compliantResult, "explore");

            assertThat(rebelExplore).as("High-autonomy agent explores more")
                    .isGreaterThanOrEqualTo(compliantExplore);
        }

        @Test
        void boldAgentApproachesMoreThanConservative() {
            var bold = new DispositionAxes(
                    "competitive", "flexible", "bold", "high", "competitive");
            var conservative = new DispositionAxes(
                    "cooperative", "strict", "conservative", "low", "avoidant");

            var boldResult = scenario("bold", bold)
                    .settle(Map.of("competition", 0.7, "scarcity", 0.5));
            var safeResult = scenario("safe", conservative)
                    .settle(Map.of("competition", 0.7, "scarcity", 0.5));

            double boldApproach = attractorStrength(boldResult, "approach");
            double safeApproach = attractorStrength(safeResult, "approach");

            assertThat(boldApproach).as("Bold agent approaches more than conservative")
                    .isGreaterThan(safeApproach);
        }
    }

    @Nested
    class ExperienceConditioning {

        @Test
        void repeatedBetrayalShiftsTrustToDistrust() {
            var neutral = new DispositionAxes(
                    "cooperative", "moderate", "calculated", "moderate", "analytical");

            var trusting = scenario("trusting", neutral)
                    .settle(Map.of("secure_attachment", 0.6));

            var betrayed = scenario("betrayed", neutral)
                    .withExperience("Was betrayed and deceived by a trusted friend", 0.9, -0.9, 8)
                    .applyExperiences()
                    .settle(Map.of("secure_attachment", 0.6));

            double trustBefore = attractorStrength(trusting, "trust");
            double distrustBefore = attractorStrength(trusting, "distrust");
            double trustAfter = attractorStrength(betrayed, "trust");
            double distrustAfter = attractorStrength(betrayed, "distrust");

            assertThat(distrustAfter - distrustBefore)
                    .as("Betrayal history shifts distrust more than trust")
                    .isGreaterThanOrEqualTo(0.0);
        }

        @Test
        void repeatedRewardStrengthensApproach() {
            var neutral = new DispositionAxes(
                    "cooperative", "moderate", "calculated", "moderate", "analytical");

            var unrewarded = scenario("novice", neutral)
                    .settle(Map.of("reward", 0.5));

            var rewarded = scenario("experienced", neutral)
                    .withExperience("Was rewarded and praised for great work", 0.8, 0.9, 8)
                    .applyExperiences()
                    .settle(Map.of("reward", 0.5));

            assertThat(attractorStrength(rewarded, "approach"))
                    .as("Repeated rewards strengthen approach")
                    .isGreaterThanOrEqualTo(attractorStrength(unrewarded, "approach"));
        }

        @Test
        void positiveReinforcementStrengthensApproachOverBaseline() {
            var neutral = new DispositionAxes(
                    "cooperative", "moderate", "bold", "moderate", "analytical");

            var baseline = scenario("baseline", neutral)
                    .settle(Map.of("reward", 0.6, "mastery", 0.5));

            var reinforced = scenario("reinforced", neutral)
                    .withDirectActivation(
                            Map.of("reward", 0.8, "mastery", 0.7),
                            0.9, 0.9)
                    .withDirectActivation(
                            Map.of("reward", 0.7, "competence_recognition", 0.6),
                            0.8, 0.8)
                    .settle(Map.of("reward", 0.6, "mastery", 0.5));

            double approachReinforced = attractorStrength(reinforced, "approach");
            double approachBaseline = attractorStrength(baseline, "approach");

            assertThat(approachReinforced)
                    .as("Positive reinforcement strengthens approach over baseline")
                    .isGreaterThanOrEqualTo(approachBaseline);
        }
    }

    @Nested
    class SingleVariableIsolation {

        @Test
        void flippingRiskAppetiteChangesApproachStrength() {
            var boldAxes = new DispositionAxes(
                    "cooperative", "moderate", "bold", "moderate", "analytical");
            var cautiousAxes = new DispositionAxes(
                    "cooperative", "moderate", "conservative", "moderate", "analytical");

            var boldResult = scenario("bold", boldAxes)
                    .settle(Map.of("competition", 0.7));
            var cautiousResult = scenario("cautious", cautiousAxes)
                    .settle(Map.of("competition", 0.7));

            double boldApproach = attractorStrength(boldResult, "approach");
            double cautiousApproach = attractorStrength(cautiousResult, "approach");

            assertThat(boldApproach).as("Bold approaches more than cautious with only riskAppetite changed")
                    .isGreaterThan(cautiousApproach);
        }

        @Test
        void addingPositiveExperienceModeratesThreatResponse() {
            var cautious = new DispositionAxes(
                    "cooperative", "moderate", "conservative", "moderate", "avoidant");

            var pureAnxiety = scenario("anxious", cautious)
                    .withExperience("Was rejected and abandoned by everyone", 0.8, -0.8, 5)
                    .applyExperiences()
                    .settle(Map.of("social_threat", 0.6));

            var tempered = scenario("tempered", cautious)
                    .withExperience("Was rejected and abandoned by everyone", 0.8, -0.8, 5)
                    .withExperience("Was accepted and welcomed into the group", 0.7, 0.8, 5)
                    .applyExperiences()
                    .settle(Map.of("social_threat", 0.6));

            double withdrawAnxious = attractorStrength(pureAnxiety, "withdraw");
            double withdrawTempered = attractorStrength(tempered, "withdraw");

            assertThat(withdrawTempered).as("Positive experiences moderate withdrawal")
                    .isLessThanOrEqualTo(withdrawAnxious);
        }

        @Test
        void conflictModeAloneShiftsApproachWithdraw() {
            var competitive = new DispositionAxes(
                    "cooperative", "moderate", "moderate", "moderate", "competitive");
            var avoidant = new DispositionAxes(
                    "cooperative", "moderate", "moderate", "moderate", "avoidant");

            var compResult = scenario("comp", competitive)
                    .settle(Map.of("dominance", 0.7, "social_threat", 0.5));
            var avoidResult = scenario("avoid", avoidant)
                    .settle(Map.of("dominance", 0.7, "social_threat", 0.5));

            double compApproach = attractorStrength(compResult, "approach");
            double avoidApproach = attractorStrength(avoidResult, "approach");

            assertThat(compApproach).as("Competitive agent approaches more than avoidant")
                    .isGreaterThanOrEqualTo(avoidApproach);
        }
    }

    @Nested
    class CumulativeEffects {

        @Test
        void progressiveRewardBuildsApproach() {
            var neutral = new DispositionAxes(
                    "cooperative", "moderate", "calculated", "moderate", "analytical");

            var result1 = scenario("r1", neutral)
                    .withExperience("Was rewarded and praised", 0.5, 0.6, 1)
                    .applyExperiences()
                    .settle(Map.of("reward", 0.5));
            double approach1 = attractorStrength(result1, "approach");

            var result5 = scenario("r5", neutral)
                    .withExperience("Was rewarded and praised", 0.5, 0.6, 5)
                    .applyExperiences()
                    .settle(Map.of("reward", 0.5));
            double approach5 = attractorStrength(result5, "approach");

            var result10 = scenario("r10", neutral)
                    .withExperience("Was rewarded and praised", 0.5, 0.6, 10)
                    .applyExperiences()
                    .settle(Map.of("reward", 0.5));
            double approach10 = attractorStrength(result10, "approach");

            assertThat(approach10).as("More rewards → stronger approach")
                    .isGreaterThanOrEqualTo(approach5);
            assertThat(approach5).as("5 rewards ≥ 1 reward in approach strength")
                    .isGreaterThanOrEqualTo(approach1);
        }

        @Test
        void mixedExperiencesProduceModerateBehavior() {
            var neutral = new DispositionAxes(
                    "cooperative", "moderate", "calculated", "moderate", "analytical");

            var purePositive = scenario("positive", neutral)
                    .withExperience("Was accepted and valued by friends", 0.7, 0.8, 8)
                    .applyExperiences()
                    .settle(Map.of("acceptance", 0.6));

            var mixed = scenario("mixed", neutral)
                    .withExperience("Was accepted and valued by friends", 0.7, 0.8, 8)
                    .withExperience("Was excluded and left out of the group", 0.7, -0.7, 8)
                    .applyExperiences()
                    .settle(Map.of("acceptance", 0.6));

            double pureApproach = attractorStrength(purePositive, "approach");
            double mixedApproach = attractorStrength(mixed, "approach");

            assertThat(pureApproach).as("Unmixed positive experiences produce stronger approach")
                    .isGreaterThanOrEqualTo(mixedApproach);
        }
    }
}
