package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.eidos.api.GoalPriority;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeductiveGoalProposalTest {

    @Test
    void primaryAxisReturnsStrongestContribution() {
        var proposal = new DeductiveGoalProposal(
                "eliminate-threat", "Remove the threat",
                "Greed + desperation + opportunity",
                Map.of(DriveAxis.COMPETENCE, 0.3, DriveAxis.AUTONOMY, 0.9),
                null, null);
        assertThat(proposal.primaryAxis()).isEqualTo(DriveAxis.AUTONOMY);
    }

    @Test
    void primaryIntensityReturnsHighestValue() {
        var proposal = new DeductiveGoalProposal(
                "goal", "desc", "reason",
                Map.of(DriveAxis.CURIOSITY, 0.4, DriveAxis.COMPETENCE, 0.8),
                null, null);
        assertThat(proposal.primaryIntensity()).isEqualTo(0.8);
    }

    @Test
    void toDriveGoalProposalConvertsCorrectly() {
        var proposal = new DeductiveGoalProposal(
                "seize-inheritance", "Eliminate benefactor to inherit",
                "Personality greed + belief about will + financial need",
                Map.of(DriveAxis.AUTONOMY, 0.9, DriveAxis.COMPETENCE, 0.5),
                GoalPriority.PRIMARY,
                Map.of("belief", "will-inheritance"));
        var drive = proposal.toDriveGoalProposal();
        assertThat(drive.axis()).isEqualTo(DriveAxis.AUTONOMY);
        assertThat(drive.goalName()).isEqualTo("seize-inheritance");
        assertThat(drive.driveIntensity()).isEqualTo(0.9);
        assertThat(drive.suggestedPriority()).isEqualTo(GoalPriority.PRIMARY);
        assertThat(drive.proposalAttributes()).containsEntry("source", "deductive");
        assertThat(drive.proposalAttributes()).containsEntry("belief", "will-inheritance");
    }

    @Test
    void emptyContributionsDefaultToCuriosity() {
        var proposal = new DeductiveGoalProposal(
                "goal", "desc", "reason", Map.of(), null, null);
        assertThat(proposal.primaryAxis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(proposal.primaryIntensity()).isEqualTo(0.5);
    }

    @Test
    void requiresNonNullFields() {
        assertThatThrownBy(() -> new DeductiveGoalProposal(
                null, "d", "r", Map.of(), null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DeductiveGoalProposal(
                "g", null, "r", Map.of(), null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DeductiveGoalProposal(
                "g", "d", null, Map.of(), null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void driveContributionsAreImmutable() {
        var mutable = new HashMap<>(Map.of(DriveAxis.CURIOSITY, 0.7));
        var proposal = new DeductiveGoalProposal("g", "d", "r", mutable, null, null);
        mutable.put(DriveAxis.COMPETENCE, 0.5);
        assertThat(proposal.driveContributions()).hasSize(1);
    }

    @Test
    void attributesAreImmutable() {
        var mutable = new HashMap<>(Map.of("key", "val"));
        var proposal = new DeductiveGoalProposal("g", "d", "r", Map.of(), null, mutable);
        mutable.put("extra", "val2");
        assertThat(proposal.attributes()).hasSize(1);
    }
}
