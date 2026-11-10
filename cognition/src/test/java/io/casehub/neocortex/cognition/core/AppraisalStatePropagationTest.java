package io.casehub.neocortex.cognition.core;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveComposer;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveSource;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.MoodPromptSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;

class AppraisalStatePropagationTest {

    @Test
    void moodSignalNotVisibleUntilReTick() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        mood.record(new MoodSignal.InteractionAppraisal(0, 0, 0, "init"), "a1", "t1");
        mood.tick("a1", "t1");

        assertThat(mood.currentMood("a1", "t1").orElseThrow().pleasure())
                .isCloseTo(0, offset(0.01));

        mood.record(new MoodSignal.DirectShift(-0.5, 0.6, -0.3, "appraisal-emotions"), "a1", "t1");

        assertThat(mood.currentMood("a1", "t1").orElseThrow().pleasure())
                .isCloseTo(0, offset(0.01));

        mood.tick("a1", "t1");
        var updated = mood.currentMood("a1", "t1").orElseThrow();
        assertThat(updated.pleasure()).isLessThan(-0.2);
        assertThat(updated.arousal()).isGreaterThan(0.2);
    }

    @Test
    void moodPromptSectionRendersAppraisalAffectedState() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        mood.record(new MoodSignal.InteractionAppraisal(0, 0, 0, "init"), "a1", "t1");
        mood.tick("a1", "t1");

        var section = new MoodPromptSection(mood);
        var ctx = new CognitionRenderContext("a1", "t1", null);

        assertThat(section.render(ctx)).contains("emotionally even");

        mood.record(new MoodSignal.DirectShift(-0.5, 0.6, -0.3, "appraisal-emotions"), "a1", "t1");
        mood.tick("a1", "t1");

        var rendered = section.render(ctx);
        assertThat(rendered).doesNotContain("emotionally even");
        assertThat(rendered).contains("tense");
    }

    @Test
    void drivesShiftWhenMoodUpdatedFromAppraisal() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        mood.record(new MoodSignal.InteractionAppraisal(0, 0, 0, "init"), "a1", "t1");
        mood.tick("a1", "t1");

        DriveSource stub = (agentId, tenantId) -> new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "stub");
        var drives = new DriveOrchestrator(stub, stub, stub, stub, mood,
                new DriveComposer(), DriveConfig.defaults());

        var descriptor = mock(AgentDescriptor.class);
        drives.tick("a1", "t1", descriptor);
        var baseline = drives.currentDrives("a1", "t1").orElseThrow()
                .drives().get(DriveAxis.CURIOSITY).intensity();

        mood.record(new MoodSignal.DirectShift(-0.5, 0.6, -0.3, "appraisal-emotions"), "a1", "t1");
        mood.tick("a1", "t1");
        drives.tick("a1", "t1", descriptor);

        var updated = drives.currentDrives("a1", "t1").orElseThrow()
                .drives().get(DriveAxis.CURIOSITY).intensity();

        assertThat(updated).isNotEqualTo(baseline);
    }
}
