package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant;
import io.casehub.neocortex.cognition.appraisal.NoOpAppraisalStrategy;
import io.casehub.neocortex.cognition.appraisal.NoOpSalienceStrategy;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.core.CognitionPhase;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmotionRegulationOrderingTest {

    @Test
    void regulationParticipantRegisteredAfterAppraisal() {
        var config = CognitionConfig.none()
                .with("appraisal", true)
                .with("regulation", true);
        var mood = mock(MoodOrchestrator.class);
        var drives = mock(DriveOrchestrator.class);

        var core = new CognitionCore(mood, drives, null, null, null, null, null, null,
                null, config, null);

        core.configureAppraisal(new NoOpAppraisalStrategy(), new NoOpSalienceStrategy());
        core.configureRegulation();

        assertThat(core.regulationParticipant()).isNotNull();
    }

    @Test
    void regulationNotConfiguredWhenDisabled() {
        var config = CognitionConfig.none().with("appraisal", true);
        var mood = mock(MoodOrchestrator.class);
        var drives = mock(DriveOrchestrator.class);

        var core = new CognitionCore(mood, drives, null, null, null, null, null, null,
                null, config, null);

        core.configureAppraisal(new NoOpAppraisalStrategy(), new NoOpSalienceStrategy());
        core.configureRegulation();

        assertThat(core.regulationParticipant()).isNull();
    }

    @Test
    void regulationRequiresAppraisalConfigured() {
        var config = CognitionConfig.none().with("regulation", true);
        var mood = mock(MoodOrchestrator.class);
        var drives = mock(DriveOrchestrator.class);

        var core = new CognitionCore(mood, drives, null, null, null, null, null, null,
                null, config, null);

        // Don't configure appraisal
        core.configureRegulation();

        assertThat(core.regulationParticipant()).isNull();
    }
}
