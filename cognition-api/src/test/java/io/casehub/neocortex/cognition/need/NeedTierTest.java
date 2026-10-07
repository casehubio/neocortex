package io.casehub.neocortex.cognition.need;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NeedTierTest {

    @Test
    void fromDriveAxis_curiosityMapsToUnderstanding() {
        assertThat(NeedTier.fromDriveAxis(DriveAxis.CURIOSITY)).isEqualTo(NeedTier.UNDERSTANDING);
    }

    @Test
    void fromDriveAxis_competenceMapsToSelfExpression() {
        assertThat(NeedTier.fromDriveAxis(DriveAxis.COMPETENCE)).isEqualTo(NeedTier.SELF_EXPRESSION);
    }

    @Test
    void fromDriveAxis_affiliationMapsToSocial() {
        assertThat(NeedTier.fromDriveAxis(DriveAxis.AFFILIATION)).isEqualTo(NeedTier.SOCIAL);
    }

    @Test
    void fromDriveAxis_autonomyMapsToSelfExpression() {
        assertThat(NeedTier.fromDriveAxis(DriveAxis.AUTONOMY)).isEqualTo(NeedTier.SELF_EXPRESSION);
    }
}
