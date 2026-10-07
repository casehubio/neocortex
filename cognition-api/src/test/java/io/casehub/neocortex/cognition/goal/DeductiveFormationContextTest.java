package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeductiveFormationContextTest {

    @Test
    void requiresNonNullAgentAndTenant() {
        var profile = new DriveProfile("a", "t", Map.of(), 0.0, DriveAxis.CURIOSITY, Instant.now());
        assertThatThrownBy(() -> new DeductiveFormationContext(
                null, "t", profile, null, List.of(), List.of(), null, List.of(), 3))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DeductiveFormationContext(
                "a", null, profile, null, List.of(), List.of(), null, List.of(), 3))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresNonNullDriveProfile() {
        assertThatThrownBy(() -> new DeductiveFormationContext(
                "a", "t", null, null, List.of(), List.of(), null, List.of(), 3))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullListsDefaultToEmpty() {
        var profile = new DriveProfile("a", "t", Map.of(), 0.0, DriveAxis.CURIOSITY, Instant.now());
        var ctx = new DeductiveFormationContext(
                "a", "t", profile, null, null, null, null, null, 3);
        assertThat(ctx.beliefs()).isEmpty();
        assertThat(ctx.recentMemories()).isEmpty();
        assertThat(ctx.existingGoals()).isEmpty();
    }

    @Test
    void listsAreImmutable() {
        var profile = new DriveProfile("a", "t", Map.of(), 0.0, DriveAxis.CURIOSITY, Instant.now());
        var beliefs = new ArrayList<>(List.of("I am desperate"));
        var ctx = new DeductiveFormationContext(
                "a", "t", profile, null, beliefs, List.of(), null, List.of(), 3);
        beliefs.add("extra");
        assertThat(ctx.beliefs()).hasSize(1);
    }

    @Test
    void strategyInterfaceIsCallable() {
        DeductiveGoalFormationStrategy strategy = ctx -> List.of();
        var profile = new DriveProfile("a", "t", Map.of(), 0.0, DriveAxis.CURIOSITY, Instant.now());
        var ctx = new DeductiveFormationContext(
                "a", "t", profile, null, List.of(), List.of(), null, List.of(), 3);
        assertThat(strategy.propose(ctx)).isEmpty();
    }
}
