package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.EmotionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionMapperTest {

    @Test
    void fearFromNegativeWithLowCoping() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.RELEVANCE, 0.8, SecDimensions.NOVELTY, 0.7),
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, -0.6),
                SecResult.of("coping", SecDimensions.CONTROLLABILITY, 0.2, SecDimensions.ADJUSTABILITY, 0.3));

        var emotions = EmotionMapper.mapEmotions(results, "dark-corridor");

        assertThat(emotions).extracting("type").contains(EmotionType.FEAR);
        assertThat(emotions).extracting("type").doesNotContain(EmotionType.ANGER);
    }

    @Test
    void angerFromNegativeWithHighCoping() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.RELEVANCE, 0.8, SecDimensions.NOVELTY, 0.5),
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, -0.6),
                SecResult.of("coping", SecDimensions.CONTROLLABILITY, 0.8, SecDimensions.ADJUSTABILITY, 0.7));

        var emotions = EmotionMapper.mapEmotions(results, "blocker");

        assertThat(emotions).extracting("type").contains(EmotionType.ANGER);
        assertThat(emotions).extracting("type").doesNotContain(EmotionType.FEAR);
    }

    @Test
    void joyFromPositive() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.RELEVANCE, 0.7, SecDimensions.NOVELTY, 0.6),
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, 0.7));

        var emotions = EmotionMapper.mapEmotions(results, "success");

        assertThat(emotions).extracting("type").contains(EmotionType.JOY);
    }

    @Test
    void distressFromNegativeWithMidCoping() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.RELEVANCE, 0.7, SecDimensions.NOVELTY, 0.5),
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, -0.5),
                SecResult.of("coping", SecDimensions.CONTROLLABILITY, 0.45, SecDimensions.ADJUSTABILITY, 0.5));

        var emotions = EmotionMapper.mapEmotions(results, "setback");

        assertThat(emotions).extracting("type").contains(EmotionType.DISTRESS);
        assertThat(emotions).extracting("type").doesNotContain(EmotionType.FEAR, EmotionType.ANGER);
    }

    @Test
    void shameFromStandardsViolation() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.RELEVANCE, 0.6, SecDimensions.NOVELTY, 0.5),
                SecResult.of("normative",
                        SecDimensions.INTERNAL_STANDARDS, 0.3,
                        SecDimensions.EXTERNAL_STANDARDS, 0.8));

        var emotions = EmotionMapper.mapEmotions(results, "my-failure");

        assertThat(emotions).extracting("type").contains(EmotionType.SHAME);
    }

    @Test
    void reproachFromExternalStandardsViolation() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.RELEVANCE, 0.6, SecDimensions.NOVELTY, 0.5),
                SecResult.of("normative",
                        SecDimensions.INTERNAL_STANDARDS, 0.8,
                        SecDimensions.EXTERNAL_STANDARDS, 0.3));

        var emotions = EmotionMapper.mapEmotions(results, "their-failure");

        assertThat(emotions).extracting("type").contains(EmotionType.REPROACH);
        assertThat(emotions).extracting("type").doesNotContain(EmotionType.SHAME);
    }

    @Test
    void noEmotionWhenLowRelevance() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.RELEVANCE, 0.1, SecDimensions.NOVELTY, 0.3),
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, -0.8));

        var emotions = EmotionMapper.mapEmotions(results, "irrelevant");

        assertThat(emotions).isEmpty();
    }

    @Test
    void approachTendencyFromPositive() {
        var results = List.of(
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, 0.7));

        var tendencies = EmotionMapper.mapTendencies(results);

        assertThat(tendencies).extracting("readiness").contains(ActionReadiness.APPROACH);
    }

    @Test
    void avoidanceTendencyFromNegativeLowControl() {
        var results = List.of(
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, -0.6),
                SecResult.of("coping", SecDimensions.CONTROLLABILITY, 0.2));

        var tendencies = EmotionMapper.mapTendencies(results);

        assertThat(tendencies).extracting("readiness").contains(ActionReadiness.AVOIDANCE);
    }

    @Test
    void antagonismFromNegativeHighControl() {
        var results = List.of(
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, -0.6),
                SecResult.of("coping", SecDimensions.CONTROLLABILITY, 0.8));

        var tendencies = EmotionMapper.mapTendencies(results);

        assertThat(tendencies).extracting("readiness").contains(ActionReadiness.ANTAGONISM);
    }

    @Test
    void attendingFromHighNovelty() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.NOVELTY, 0.9));

        var tendencies = EmotionMapper.mapTendencies(results);

        assertThat(tendencies).extracting("readiness").contains(ActionReadiness.ATTENDING);
    }

    @Test
    void padFromAlmaTable() {
        var results = List.of(
                SecResult.of("relevance", SecDimensions.RELEVANCE, 0.8, SecDimensions.NOVELTY, 0.7),
                SecResult.of("implication", SecDimensions.CONDUCIVENESS, -0.6),
                SecResult.of("coping", SecDimensions.CONTROLLABILITY, 0.2));

        var emotions = EmotionMapper.mapEmotions(results, "threat");
        var fear = emotions.stream()
                .filter(e -> e.type() == EmotionType.FEAR).findFirst().orElseThrow();

        assertThat(fear.pad().pleasure()).isLessThan(0.0);
        assertThat(fear.pad().arousal()).isGreaterThan(0.0);
    }
}
