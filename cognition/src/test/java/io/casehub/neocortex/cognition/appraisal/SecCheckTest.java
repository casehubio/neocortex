package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.HabituationConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SecCheckTest {

    @Nested
    class RelevanceCheckTest {

        @Test
        void driveNameInObservation_highRelevance() {
            var ctx = context("knowledge gaps detected in the archive",
                    List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.8, "knowledge gaps")));
            var result = new RelevanceCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.RELEVANCE)).isGreaterThan(0.5);
        }

        @Test
        void driveTriggerInObservation_relevance() {
            var ctx = context("knowledge gaps found everywhere",
                    List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.8, "knowledge gaps")));
            var result = new RelevanceCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.RELEVANCE)).isGreaterThan(0.3);
        }

        @Test
        void noDriveMatch_lowRelevance() {
            var ctx = context("the weather is pleasant",
                    List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.8, "knowledge gaps")));
            var result = new RelevanceCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.RELEVANCE)).isCloseTo(0.0, offset(0.01));
        }

        @Test
        void noveltyDecreasesWithRepetition() {
            var check = new RelevanceCheck();
            var hab = HabituationState.empty();
            var ctx1 = contextWithHabituation("the dark corridor", List.of(), hab);
            var first = check.evaluate(ctx1);

            var hash = Integer.toHexString("the dark corridor".hashCode());
            var hab2 = hab.withObservation(hash, first.dimension(SecDimensions.NOVELTY));
            var ctx2 = contextWithHabituation("the dark corridor", List.of(), hab2);
            var second = check.evaluate(ctx2);

            assertThat(second.dimension(SecDimensions.NOVELTY))
                    .isLessThan(first.dimension(SecDimensions.NOVELTY));
        }

        @Test
        void urgencyModulatedByWeights() {
            var drives = List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.8, "research"));
            var ctx = new AppraisalContext(
                    PerceivedSituation.passThrough("curiosity research topic"),
                    drives, new io.casehub.neocortex.mindmap.AppraisalWeights(2.0, 1.0, 1.0, 1.0, 1.0),
                    HabituationConfig.defaults(), HabituationState.empty(), null);
            var result = new RelevanceCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.URGENCY))
                    .isGreaterThan(result.dimension(SecDimensions.RELEVANCE));
        }
    }

    @Nested
    class ImplicationCheckTest {

        @Test
        void positiveWords_positiveConduciveness() {
            var ctx = context("mission success — all objectives achieved", List.of());
            var result = new ImplicationCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isGreaterThan(0.0);
        }

        @Test
        void negativeWords_negativeConduciveness() {
            var ctx = context("system failed and data was lost", List.of());
            var result = new ImplicationCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isLessThan(0.0);
        }

        @Test
        void neutralText_zeroConduciveness() {
            var ctx = context("the room has four walls", List.of());
            var result = new ImplicationCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(0.0, offset(0.01));
        }
    }

    @Nested
    class CopingCheckTest {

        @Test
        void agencyWords_highControllability() {
            var ctx = context("you can choose which path to take", List.of());
            var result = new CopingCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.CONTROLLABILITY)).isGreaterThan(0.5);
        }

        @Test
        void helplessnessWords_lowControllability() {
            var ctx = context("you are trapped with no way out, impossible to escape", List.of());
            var result = new CopingCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.CONTROLLABILITY)).isLessThan(0.5);
        }

        @Test
        void manyActiveDrives_highAdjustability() {
            var drives = List.of(
                    new Drive("curiosity", DriveCategory.BASELINE, 0.7, ""),
                    new Drive("competence", DriveCategory.BASELINE, 0.6, ""),
                    new Drive("affiliation", DriveCategory.BASELINE, 0.5, ""),
                    new Drive("protection", DriveCategory.CHARACTER, 0.8, ""));
            var ctx = context("a challenge appears", drives);
            var result = new CopingCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.ADJUSTABILITY)).isGreaterThan(0.7);
        }

        @Test
        void noDrives_lowAdjustability() {
            var ctx = context("a challenge appears", List.of());
            var result = new CopingCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.ADJUSTABILITY)).isCloseTo(0.0, offset(0.01));
        }
    }

    @Nested
    class NormativeCheckTest {

        @Test
        void normViolation_lowInternalStandards() {
            var ctx = context("this betrayal was deeply wrong and unjust", List.of());
            var result = new NormativeCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS)).isLessThan(0.8);
        }

        @Test
        void normConformity_highStandards() {
            var ctx = context("fair and honest treatment for everyone", List.of());
            var result = new NormativeCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS)).isGreaterThan(0.8);
        }

        @Test
        void neutralText_defaultStandards() {
            var ctx = context("the table is made of wood", List.of());
            var result = new NormativeCheck().evaluate(ctx);
            assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS)).isCloseTo(1.0, offset(0.01));
        }
    }

    private static AppraisalContext context(String observation, List<Drive> drives) {
        return new AppraisalContext(
                PerceivedSituation.passThrough(observation),
                drives, null, HabituationConfig.defaults(),
                HabituationState.empty(), null);
    }

    private static AppraisalContext contextWithHabituation(String observation,
            List<Drive> drives, HabituationState habituation) {
        return new AppraisalContext(
                PerceivedSituation.passThrough(observation),
                drives, null, HabituationConfig.defaults(),
                habituation, null);
    }
}
