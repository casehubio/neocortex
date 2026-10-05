package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.cognition.appraisal.DriveCategory;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DriveProfileBridgeTest {

    @Test
    void allDrivesConvertsToListOfDrive() {
        var profile = new DriveProfile("a1", "t1",
                Map.of(
                        DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.8, "gaps"),
                        DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.3, "skills")
                ),
                0.55, DriveAxis.CURIOSITY, Instant.now());

        var drives = profile.allDrives();

        assertThat(drives).hasSize(2);
        assertThat(drives).allSatisfy(d -> assertThat(d.category()).isEqualTo(DriveCategory.BASELINE));

        var curiosity = drives.stream().filter(d -> d.name().equals("curiosity")).findFirst().orElseThrow();
        assertThat(curiosity.intensity()).isCloseTo(0.8, within(0.001));
        assertThat(curiosity.trigger()).isEqualTo("gaps");
    }

    @Test
    void dominantDriveNameReturnsLowercase() {
        var profile = new DriveProfile("a1", "t1",
                Map.of(DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.9, "")),
                0.9, DriveAxis.AFFILIATION, Instant.now());

        assertThat(profile.dominantDriveName()).isEqualTo("affiliation");
    }

    @Test
    void emptyDrivesProducesEmptyList() {
        var profile = new DriveProfile("a1", "t1",
                Map.of(), 0.0, DriveAxis.CURIOSITY, Instant.now());

        assertThat(profile.allDrives()).isEmpty();
    }
}
