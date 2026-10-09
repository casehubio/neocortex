package io.casehub.neocortex.cognition.relationship;

import io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityDrivenTrustTest {

    private static final RelationshipStageConfig CONFIG = RelationshipStageConfig.defaults();

    @Test
    void highTrustRateProducesHigherFamiliarity() {
        double baseline = UserModelOrchestrator.computeFamiliarity(
                8, 2, 0, CONFIG, 0);
        double cooperative = UserModelOrchestrator.computeFamiliarity(
                8, 2, 0, CONFIG, 0, 0.7);

        assertThat(cooperative)
                .as("Cooperative agent (trustFormationRate=0.7) should produce higher familiarity")
                .isGreaterThan(baseline);
    }

    @Test
    void lowTrustRateProducesLowerFamiliarity() {
        double baseline = UserModelOrchestrator.computeFamiliarity(
                8, 2, 0, CONFIG, 0);
        double suspicious = UserModelOrchestrator.computeFamiliarity(
                8, 2, 0, CONFIG, 0, 0.3);

        assertThat(suspicious)
                .as("Suspicious agent (trustFormationRate=0.3) should produce lower familiarity")
                .isLessThan(baseline);
    }

    @Test
    void defaultTrustRateMatchesOriginalBehavior() {
        double original = UserModelOrchestrator.computeFamiliarity(
                5, 3, 2, CONFIG, 0);
        double withDefault = UserModelOrchestrator.computeFamiliarity(
                5, 3, 2, CONFIG, 0, 0.5);

        assertThat(withDefault).isEqualTo(original, org.assertj.core.api.Assertions.within(0.001));
    }
}
