package io.casehub.neocortex.cognition.appraisal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DriveTest {

    @Test
    void baselineDrive() {
        var drive = new Drive("curiosity", DriveCategory.BASELINE, 0.7, "knowledge gaps detected");
        assertThat(drive.name()).isEqualTo("curiosity");
        assertThat(drive.category()).isEqualTo(DriveCategory.BASELINE);
        assertThat(drive.intensity()).isCloseTo(0.7, within(0.001));
        assertThat(drive.trigger()).isEqualTo("knowledge gaps detected");
    }

    @Test
    void characterDrive() {
        var drive = new Drive("protection", DriveCategory.CHARACTER, 0.9, "Clara is missing");
        assertThat(drive.category()).isEqualTo(DriveCategory.CHARACTER);
    }

    @Test
    void intensityClampedToValidRange() {
        assertThatThrownBy(() -> new Drive("x", DriveCategory.BASELINE, -0.1, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Drive("x", DriveCategory.BASELINE, 1.1, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameRequired() {
        assertThatThrownBy(() -> new Drive(null, DriveCategory.BASELINE, 0.5, ""))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTriggerDefaultsToEmpty() {
        var drive = new Drive("curiosity", DriveCategory.BASELINE, 0.5, null);
        assertThat(drive.trigger()).isEmpty();
    }

    @Test
    void actionTendencyValidation() {
        var t = new ActionTendency(ActionReadiness.APPROACH, 0.6, "door");
        assertThat(t.readiness()).isEqualTo(ActionReadiness.APPROACH);
        assertThat(t.intensity()).isCloseTo(0.6, within(0.001));

        assertThatThrownBy(() -> new ActionTendency(null, 0.5, ""))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActionTendency(ActionReadiness.APPROACH, 1.5, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
