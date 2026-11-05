package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.goal.DriveGoalProposal;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CognitiveLoadEstimatorTest {

    private static final String AGENT = "a1";
    private static final String TENANT = "t1";

    @Test
    void lowLoadWithMinimalActivity() {
        var drives = mock(DriveOrchestrator.class);
        when(drives.currentDrives(AGENT, TENANT)).thenReturn(Optional.of(
                driveProfile(Map.of(DriveAxis.CURIOSITY, intensity(DriveAxis.CURIOSITY, 0.5)))));

        var estimator = new CognitiveLoadEstimator(drives, null, null);
        var estimate = estimator.estimate(AGENT, TENANT);

        assertThat(estimate.level()).isEqualTo(CognitiveLoadLevel.LOW);
        assertThat(estimate.activeDrives()).isEqualTo(1);
        assertThat(estimate.activeGoals()).isEqualTo(0);
        assertThat(estimate.mentalModelTargets()).isEqualTo(0);
        assertThat(estimate.totalLoad()).isEqualTo(1);
    }

    @Test
    void moderateLoadWithMultipleSources() {
        var drives = mock(DriveOrchestrator.class);
        var goals = mock(GoalProposalOrchestrator.class);
        when(drives.currentDrives(AGENT, TENANT)).thenReturn(Optional.of(
                driveProfile(Map.of(
                        DriveAxis.CURIOSITY, intensity(DriveAxis.CURIOSITY, 0.5),
                        DriveAxis.COMPETENCE, intensity(DriveAxis.COMPETENCE, 0.3)))));
        when(goals.currentProposals(AGENT, TENANT)).thenReturn(Optional.of(
                List.of(mock(DriveGoalProposal.class), mock(DriveGoalProposal.class))));

        var estimator = new CognitiveLoadEstimator(drives, goals, null);
        var estimate = estimator.estimate(AGENT, TENANT);

        assertThat(estimate.level()).isEqualTo(CognitiveLoadLevel.MODERATE);
        assertThat(estimate.totalLoad()).isEqualTo(4);
    }

    @Test
    void highLoadWithAllSources() {
        var drives = mock(DriveOrchestrator.class);
        var goals = mock(GoalProposalOrchestrator.class);
        var mentalModel = mock(MentalModelOrchestrator.class);
        when(drives.currentDrives(AGENT, TENANT)).thenReturn(Optional.of(
                driveProfile(Map.of(
                        DriveAxis.CURIOSITY, intensity(DriveAxis.CURIOSITY, 0.5),
                        DriveAxis.COMPETENCE, intensity(DriveAxis.COMPETENCE, 0.3),
                        DriveAxis.AFFILIATION, intensity(DriveAxis.AFFILIATION, 0.4)))));
        when(goals.currentProposals(AGENT, TENANT)).thenReturn(Optional.of(
                List.of(mock(DriveGoalProposal.class), mock(DriveGoalProposal.class),
                        mock(DriveGoalProposal.class))));
        when(mentalModel.activeSnapshots(AGENT, TENANT)).thenReturn(
                List.of(mock(MentalModelSnapshot.class), mock(MentalModelSnapshot.class)));

        var estimator = new CognitiveLoadEstimator(drives, goals, mentalModel);
        var estimate = estimator.estimate(AGENT, TENANT);

        assertThat(estimate.level()).isEqualTo(CognitiveLoadLevel.HIGH);
        assertThat(estimate.totalLoad()).isEqualTo(8);
    }

    @Test
    void noDrivesReturnsLow() {
        var drives = mock(DriveOrchestrator.class);
        when(drives.currentDrives(AGENT, TENANT)).thenReturn(Optional.empty());

        var estimator = new CognitiveLoadEstimator(drives, null, null);
        var estimate = estimator.estimate(AGENT, TENANT);

        assertThat(estimate.level()).isEqualTo(CognitiveLoadLevel.LOW);
        assertThat(estimate.totalLoad()).isEqualTo(0);
    }

    private static DriveProfile driveProfile(Map<DriveAxis, DriveIntensity> drives) {
        DriveAxis dominant = drives.keySet().iterator().next();
        return new DriveProfile(AGENT, TENANT, drives, 0.5, dominant, Instant.now());
    }

    private static DriveIntensity intensity(DriveAxis axis, double value) {
        return new DriveIntensity(axis, value, "test");
    }
}
