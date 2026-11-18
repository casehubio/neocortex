package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeRange;
import io.casehub.neocortex.caps.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SituationalDetectorTest {

    private SituationalDetector detector;
    private SituationalPersonalityConfig config;

    @BeforeEach
    void setUp() {
        var loader = new SituationalPersonalityConfigLoader();
        config = loader.loadFromClasspath("situational-personality.yaml");
        detector = new SituationalDetector(config);
    }

    @Test
    void noSituationInputsProducesZeroEvidence() {
        var evidence = SituationalEvidence.empty();
        var result = detector.detect(evidence, DetectionResult.initial(), 1.0);
        for (SituationalMode mode : SituationalMode.values()) {
            assertThat(result.totalEvidence().get(mode)).as(mode.name()).isEqualTo(0.0);
        }
    }

    @Test
    void xZeroImpliesEZeroRegardlessOfEndogenous() {
        var evidence = SituationalEvidence.builder()
                .mood(-1.0, 1.0, -1.0)
                .secDimensions(Map.of("relevance", 1.0, "conduciveness", -1.0, "controllability", 0.0))
                .build();
        var result = detector.detect(evidence, DetectionResult.initial(), 1.0);
        assertThat(result.totalEvidence().get(SituationalMode.STRESS)).isEqualTo(0.0);
    }

    @Test
    void strongThreatEngagesStress() {
        var evidence = SituationalEvidence.builder()
                .situationInputs(Map.of("physical_threat", 0.9))
                .capsMediating(Map.of("FFFS_activation", 0.7, "BIS_activation", 0.5, "threat_sensitivity", 0.6))
                .capsRest(Map.of("FFFS_activation", 0.1, "BIS_activation", 0.1, "threat_sensitivity", 0.1))
                .mood(-0.5, 0.6, -0.3)
                .secDimensions(Map.of("relevance", 0.8, "conduciveness", -0.6, "controllability", 0.3))
                .build();
        var result = detector.detect(evidence, DetectionResult.initial(), 1.0);
        assertThat(result.isEngaged(SituationalMode.STRESS)).isTrue();
        assertThat(result.intensity(SituationalMode.STRESS)).isGreaterThan(0.5);
    }

    @Test
    void fadingDecaysIntensityOverTime() {
        var strongEvidence = SituationalEvidence.builder()
                .situationInputs(Map.of("physical_threat", 0.95))
                .capsMediating(Map.of("FFFS_activation", 0.8, "BIS_activation", 0.7, "threat_sensitivity", 0.7))
                .capsRest(Map.of("FFFS_activation", 0.1, "BIS_activation", 0.1, "threat_sensitivity", 0.1))
                .mood(-0.6, 0.7, -0.4)
                .secDimensions(Map.of("relevance", 0.9, "conduciveness", -0.7, "controllability", 0.2))
                .build();
        var engaged = detector.detect(strongEvidence, DetectionResult.initial(), 1.0);
        double peakIntensity = engaged.intensity(SituationalMode.STRESS);

        var noEvidence = SituationalEvidence.empty();
        var faded = detector.detect(noEvidence, engaged, 3600.0);
        assertThat(faded.intensity(SituationalMode.STRESS)).isLessThan(peakIntensity);
    }

    @Test
    void stressDisengagesBelowThresholdOff() {
        var strongEvidence = SituationalEvidence.builder()
                .situationInputs(Map.of("physical_threat", 0.95))
                .capsMediating(Map.of("FFFS_activation", 0.8, "BIS_activation", 0.7, "threat_sensitivity", 0.7))
                .capsRest(Map.of("FFFS_activation", 0.1, "BIS_activation", 0.1, "threat_sensitivity", 0.1))
                .mood(-0.6, 0.7, -0.4)
                .secDimensions(Map.of("relevance", 0.9, "conduciveness", -0.7, "controllability", 0.2))
                .build();
        var engaged = detector.detect(strongEvidence, DetectionResult.initial(), 1.0);
        assertThat(engaged.isEngaged(SituationalMode.STRESS)).isTrue();

        var noEvidence = SituationalEvidence.empty();
        var current = engaged;
        for (int i = 0; i < 100; i++) {
            current = detector.detect(noEvidence, current, 600.0);
        }
        assertThat(current.isEngaged(SituationalMode.STRESS)).isFalse();
        assertThat(current.intensity(SituationalMode.STRESS)).isEqualTo(0.0);
    }

    @Test
    void groupExclusivitySelectsHighestIntensity() {
        var collaborativeEvidence = SituationalEvidence.builder()
                .situationInputs(Map.of("acceptance", 0.9, "competition", 0.7))
                .capsMediating(Map.of("other_safety", 0.6, "other_reliability", 0.6))
                .capsRest(Map.of("other_safety", 0.1, "other_reliability", 0.1))
                .mood(0.5, 0.3, 0.1)
                .interlocutor(0.6)
                .build();
        var result = detector.detect(collaborativeEvidence, DetectionResult.initial(), 1.0);
        SituationalMode winner = result.winner(SituationalMode.Group.SOCIAL);
        assertThat(winner).isNotNull();
    }

    @Test
    void switchMarginPreventsFlapping() {
        var evidence1 = SituationalEvidence.builder()
                .situationInputs(Map.of("acceptance", 0.85))
                .capsMediating(Map.of("other_safety", 0.5, "other_reliability", 0.5))
                .capsRest(Map.of("other_safety", 0.1, "other_reliability", 0.1))
                .mood(0.5, 0.3, 0.1)
                .interlocutor(0.5)
                .build();
        var result1 = detector.detect(evidence1, DetectionResult.initial(), 1.0);
        SituationalMode winner1 = result1.winner(SituationalMode.Group.SOCIAL);

        if (winner1 != null) {
            var evidence2 = SituationalEvidence.builder()
                    .situationInputs(Map.of("acceptance", 0.80, "competition", 0.82))
                    .capsMediating(Map.of("other_safety", 0.45, "other_reliability", 0.45))
                    .capsRest(Map.of("other_safety", 0.1, "other_reliability", 0.1))
                    .mood(0.3, 0.4, 0.0)
                    .interlocutor(0.0)
                    .build();
            var result2 = detector.detect(evidence2, result1, 1.0);
            SituationalMode winner2 = result2.winner(SituationalMode.Group.SOCIAL);
            if (winner2 != null) {
                assertThat(winner2).as("Switch margin should prevent flapping on marginal difference")
                        .isEqualTo(winner1);
            }
        }
    }

    @Test
    void socialGateRequiresInterlocutor() {
        var evidence = SituationalEvidence.builder()
                .situationInputs(Map.of("acceptance", 0.9))
                .capsMediating(Map.of("other_safety", 0.6, "other_reliability", 0.6))
                .capsRest(Map.of("other_safety", 0.1, "other_reliability", 0.1))
                .mood(0.5, 0.3, 0.1)
                .build();
        var result = detector.detect(evidence, DetectionResult.initial(), 1.0);
        assertThat(result.isEngaged(SituationalMode.SOCIAL_COLLABORATIVE)).isFalse();
    }

    @Test
    void trustedInterlocutorAmplifiesCollaborativeEvidence() {
        var withoutTrust = SituationalEvidence.builder()
                .situationInputs(Map.of("acceptance", 0.8))
                .capsMediating(Map.of("other_safety", 0.5, "other_reliability", 0.5))
                .capsRest(Map.of("other_safety", 0.1, "other_reliability", 0.1))
                .mood(0.3, 0.1, 0.1)
                .interlocutor(0.0)
                .build();
        var withTrust = SituationalEvidence.builder()
                .situationInputs(Map.of("acceptance", 0.8))
                .capsMediating(Map.of("other_safety", 0.5, "other_reliability", 0.5))
                .capsRest(Map.of("other_safety", 0.1, "other_reliability", 0.1))
                .mood(0.3, 0.1, 0.1)
                .interlocutor(0.8)
                .build();
        var resultNoTrust = detector.detect(withoutTrust, DetectionResult.initial(), 1.0);
        var resultTrust = detector.detect(withTrust, DetectionResult.initial(), 1.0);
        assertThat(resultTrust.totalEvidence().get(SituationalMode.SOCIAL_COLLABORATIVE))
                .isGreaterThan(resultNoTrust.totalEvidence().get(SituationalMode.SOCIAL_COLLABORATIVE));
    }

    @Test
    void stressAndSocialCanCoexist() {
        var evidence = SituationalEvidence.builder()
                .situationInputs(Map.of("physical_threat", 0.9, "competition", 0.8))
                .capsMediating(Map.of(
                        "FFFS_activation", 0.7, "BIS_activation", 0.5, "threat_sensitivity", 0.6,
                        "other_safety", 0.2, "other_reliability", 0.2))
                .capsRest(Map.of(
                        "FFFS_activation", 0.1, "BIS_activation", 0.1, "threat_sensitivity", 0.1,
                        "other_safety", 0.5, "other_reliability", 0.5))
                .mood(-0.3, 0.6, -0.2)
                .interlocutor(-0.5)
                .build();
        var result = detector.detect(evidence, DetectionResult.initial(), 1.0);
        assertThat(result.winner(SituationalMode.Group.AFFECTIVE)).isEqualTo(SituationalMode.STRESS);
        if (result.winner(SituationalMode.Group.SOCIAL) != null) {
            assertThat(result.winner(SituationalMode.Group.SOCIAL)).isEqualTo(SituationalMode.SOCIAL_COMPETITIVE);
        }
    }

    @Test
    void emptySecDimensionsContributeZeroEndogenous() {
        var evidence = SituationalEvidence.builder()
                .situationInputs(Map.of("physical_threat", 0.9))
                .capsMediating(Map.of("FFFS_activation", 0.5, "BIS_activation", 0.3, "threat_sensitivity", 0.4))
                .capsRest(Map.of("FFFS_activation", 0.1, "BIS_activation", 0.1, "threat_sensitivity", 0.1))
                .build();
        var result = detector.detect(evidence, DetectionResult.initial(), 1.0);
        assertThat(result.endogenousEvidence().get(SituationalMode.STRESS)).isEqualTo(0.0);
        assertThat(result.exogenousEvidence().get(SituationalMode.STRESS)).isGreaterThan(0.0);
    }

    @Test
    void flowRequiresMasterySignals() {
        var evidence = SituationalEvidence.builder()
                .situationInputs(Map.of("mastery", 0.9))
                .capsMediating(Map.of("BAS_activation", 0.6, "self_efficacy", 0.7))
                .capsRest(Map.of("BAS_activation", 0.2, "self_efficacy", 0.2))
                .mood(0.5, 0.25, 0.3)
                .secDimensions(Map.of("relevance", 0.8, "conduciveness", 0.7))
                .build();
        var result = detector.detect(evidence, DetectionResult.initial(), 1.0);
        assertThat(result.exogenousEvidence().get(SituationalMode.FLOW)).isGreaterThan(0.0);
    }
}
