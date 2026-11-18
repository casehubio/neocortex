package io.casehub.neocortex.cognition.situational;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class PersonalityStateIntegratorTest {

    private static final String EL = "Emotional Lability";
    private static final String AX = "Anxiousness";
    private static final Set<String> FACETS = Set.of(EL, AX);
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private PersonalityStateIntegrator integrator;

    @BeforeEach
    void setUp() {
        integrator = new PersonalityStateIntegrator(DynamicsConfig.defaults());
    }

    @Test
    void initialStateIsAtRest() {
        var state = PersonalityState.initial(FACETS, T0);
        assertThat(state.facet(EL).displacement()).isZero();
        assertThat(state.facet(EL).homeOffset()).isZero();
        assertThat(state.facet(EL).resilience()).isEqualTo(1.0);
    }

    @Test
    void pullMovesDisplacementTowardDelta() {
        var state = PersonalityState.initial(FACETS, T0);
        state = integrator.pull(state, Map.of(EL, 0.20), 1.0, true, T0);
        assertThat(state.facet(EL).displacement()).isCloseTo(0.06, offset(0.01));
    }

    @Test
    void repeatedPullsConvergeOnDelta() {
        var state = PersonalityState.initial(FACETS, T0);
        for (int i = 0; i < 20; i++) {
            state = integrator.pull(state, Map.of(EL, 0.20), 1.0, true, T0);
        }
        assertThat(state.facet(EL).displacement()).isCloseTo(0.20, offset(0.01));
    }

    @Test
    void briefExposureReturnsNearlyToHome() {
        var state = PersonalityState.initial(FACETS, T0);
        Instant t = T0;
        for (int i = 0; i < 10; i++) {
            state = integrator.pull(state, Map.of(EL, 0.20), 1.0, true, t);
            t = t.plus(Duration.ofMinutes(1));
            state = integrator.advance(state, t);
        }
        state = integrator.advance(state, t.plus(Duration.ofHours(24)));
        assertThat(Math.abs(state.facet(EL).displacement())).isLessThan(0.005);
        assertThat(Math.abs(state.facet(EL).homeOffset())).isLessThan(0.002);
    }

    @Test
    void prolongedExposureDeformsPermanently() {
        var state = PersonalityState.initial(FACETS, T0);
        Instant t = T0;
        for (int day = 0; day < 30; day++) {
            for (int turn = 0; turn < 100; turn++) {
                state = integrator.pull(state, Map.of(EL, 0.20), 1.0, true, t);
                t = t.plus(Duration.ofMinutes(1));
                state = integrator.advance(state, t);
            }
            t = t.plus(Duration.ofHours(22).plusMinutes(20));
            state = integrator.advance(state, t);
        }
        t = t.plus(Duration.ofHours(24));
        state = integrator.advance(state, t);
        assertThat(state.facet(EL).homeOffset()).isGreaterThan(0.01);
        assertThat(state.facet(EL).resilience()).isLessThan(0.5);
    }

    @Test
    void idleAdvanceReturnsDTowardZero() {
        var state = PersonalityState.initial(FACETS, T0);
        state = integrator.pull(state, Map.of(EL, 0.20), 1.0, true, T0);
        state = integrator.advance(state, T0.plus(Duration.ofDays(7)));
        assertThat(Math.abs(state.facet(EL).displacement())).isLessThan(0.001);
    }

    @Test
    void facetsWithZeroDeltaAccrueNoLoad() {
        var state = PersonalityState.initial(FACETS, T0);
        Instant t = T0;
        for (int i = 0; i < 10; i++) {
            state = integrator.pull(state, Map.of(EL, 0.20), 1.0, true, t);
            t = t.plus(Duration.ofMinutes(1));
            state = integrator.advance(state, t);
        }
        assertThat(state.facet(AX).load()).isZero();
        assertThat(state.facet(AX).homeOffset()).isZero();
    }

    @Test
    void cadenceIndependence() {
        var state1 = PersonalityState.initial(FACETS, T0);
        var state2 = PersonalityState.initial(FACETS, T0);
        Instant t = T0;

        for (int i = 0; i < 10; i++) {
            state1 = integrator.pull(state1, Map.of(EL, 0.20), 1.0, true, t);
            state2 = integrator.pull(state2, Map.of(EL, 0.20), 1.0, true, t);
            t = t.plus(Duration.ofMinutes(1));
            state1 = integrator.advance(state1, t);
            state2 = integrator.advance(state2, t);
        }

        Instant end = t.plus(Duration.ofDays(14));
        state1 = integrator.advance(state1, end);

        Instant t2 = t;
        while (t2.isBefore(end)) {
            Instant next = t2.plus(Duration.ofMinutes(5));
            if (next.isAfter(end)) next = end;
            state2 = integrator.advance(state2, next);
            t2 = next;
        }

        assertThat(state1.facet(EL).homeOffset()).isCloseTo(state2.facet(EL).homeOffset(), offset(1e-5));
        assertThat(state1.facet(EL).resilience()).isCloseTo(state2.facet(EL).resilience(), offset(1e-5));
    }

    @Test
    void doseResponseIsMonotonic() {
        double offset5 = simulateDailyStress(5, 14);
        double offset30 = simulateDailyStress(30, 14);
        double offset100 = simulateDailyStress(100, 14);

        assertThat(offset5).isLessThan(offset30);
        assertThat(offset30).isLessThan(offset100);
    }

    @Test
    void exposureWindowWeighting() {
        var state1 = PersonalityState.initial(Set.of(EL), T0);
        state1 = integrator.pull(state1, Map.of(EL, 0.20), 1.0, false, T0);
        state1 = integrator.advance(state1, T0.plus(Duration.ofHours(1)));
        double offsetOutsideWindow = Math.abs(state1.facet(EL).homeOffset());

        var state2 = PersonalityState.initial(Set.of(EL), T0);
        state2 = integrator.pull(state2, Map.of(EL, 0.20), 1.0, true, T0);
        state2 = integrator.advance(state2, T0.plus(Duration.ofHours(1)));
        double offsetInsideWindow = Math.abs(state2.facet(EL).homeOffset());

        assertThat(offsetInsideWindow).isGreaterThan(offsetOutsideWindow);
    }

    @Test
    void pullClampsCurrentToUnitRange() {
        var state = PersonalityState.initial(FACETS, T0);
        for (int i = 0; i < 100; i++) {
            state = integrator.pull(state, Map.of(EL, 0.50), 1.0, true, T0);
        }
        double home = 0.5 + state.facet(EL).homeOffset();
        double current = home + state.facet(EL).displacement();
        assertThat(current).isBetween(0.0, 1.0);
    }

    @Test
    void resilienceNeverDropsBelowFloor() {
        var state = PersonalityState.initial(FACETS, T0);
        Instant t = T0;
        for (int day = 0; day < 365; day++) {
            for (int turn = 0; turn < 100; turn++) {
                state = integrator.pull(state, Map.of(EL, 0.25), 1.0, true, t);
                t = t.plus(Duration.ofMinutes(1));
                state = integrator.advance(state, t);
            }
            t = t.plus(Duration.ofHours(22).plusMinutes(20));
            state = integrator.advance(state, t);
        }
        assertThat(state.facet(EL).resilience()).isGreaterThanOrEqualTo(DynamicsConfig.defaults().rMin());
    }

    @Test
    void homeOffsetNeverExceedsDMax() {
        var state = PersonalityState.initial(FACETS, T0);
        Instant t = T0;
        for (int day = 0; day < 365; day++) {
            for (int turn = 0; turn < 100; turn++) {
                state = integrator.pull(state, Map.of(EL, 0.25), 1.0, true, t);
                t = t.plus(Duration.ofMinutes(1));
                state = integrator.advance(state, t);
            }
            t = t.plus(Duration.ofHours(22).plusMinutes(20));
            state = integrator.advance(state, t);
        }
        assertThat(Math.abs(state.facet(EL).homeOffset())).isLessThanOrEqualTo(DynamicsConfig.defaults().dMax() + 1e-9);
    }

    private double simulateDailyStress(int turnsPerDay, int days) {
        var state = PersonalityState.initial(Set.of(EL), T0);
        Instant t = T0;
        for (int day = 0; day < days; day++) {
            for (int turn = 0; turn < turnsPerDay; turn++) {
                state = integrator.pull(state, Map.of(EL, 0.20), 1.0, true, t);
                t = t.plus(Duration.ofMinutes(1));
                state = integrator.advance(state, t);
            }
            t = t.plus(Duration.ofHours(24).minus(Duration.ofMinutes(turnsPerDay)));
            state = integrator.advance(state, t);
        }
        t = t.plus(Duration.ofHours(24));
        state = integrator.advance(state, t);
        return state.facet(EL).homeOffset();
    }
}
