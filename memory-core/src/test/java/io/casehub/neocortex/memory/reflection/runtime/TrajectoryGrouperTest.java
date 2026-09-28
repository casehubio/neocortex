package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrajectoryGrouperTest {

    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");

    private Memory mem(String memoryId, String caseId, String turnId, String eventType,
                       Instant createdAt, Map<String, String> extraAttrs) {
        var attrs = new HashMap<>(Map.of(
            ExperienceAttributeKeys.EVENT_TYPE, eventType,
            ExperienceAttributeKeys.TIMESTAMP, createdAt.toString()));
        if (turnId != null) attrs.put(ExperienceAttributeKeys.TURN_ID, turnId);
        attrs.putAll(extraAttrs);
        return new Memory(memoryId, Subject.of("agent", "a1"), EXPERIENCE, "t1",
            caseId, "description", attrs, createdAt, null, null, null, null, null, null);
    }

    private Memory mem(String memoryId, String caseId, String turnId, String eventType,
                       Instant createdAt) {
        return mem(memoryId, caseId, turnId, eventType, createdAt, Map.of());
    }

    @Test
    void groupsByCaseId() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "observation", now),
            mem("m2", "case-B", "t1", "action", now.plusSeconds(1)),
            mem("m3", "case-A", "t2", "outcome", now.plusSeconds(2))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories).hasSize(2);
        assertThat(trajectories).extracting(Trajectory::caseId)
            .containsExactlyInAnyOrder("case-A", "case-B");
    }

    @Test
    void ordersStepsByTimestamp() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t2", "action", now.plusSeconds(10)),
            mem("m2", "case-A", "t1", "observation", now)
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories).hasSize(1);
        var steps = trajectories.get(0).steps();
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).turnId()).isEqualTo("t1");
        assertThat(steps.get(1).turnId()).isEqualTo("t2");
    }

    @Test
    void classifiesFailureViaOutcomeStatus() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "observation", now),
            mem("m2", "case-A", "t2", "outcome", now.plusSeconds(1),
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failed",
                       ExperienceAttributeKeys.RESULT, "timeout"))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.FAILURE);
    }

    @Test
    void classifiesSuccessViaOutcomeStatus() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "outcome", now,
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "success",
                       ExperienceAttributeKeys.RESULT, "completed"))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.SUCCESS);
    }

    @Test
    void classifiesNeutralWhenNoOutcomeStatus() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "outcome", now,
                Map.of(ExperienceAttributeKeys.RESULT, "completed"))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.NEUTRAL);
    }

    @Test
    void classifiesNeutralWhenNoOutcomeEvents() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "observation", now),
            mem("m2", "case-A", "t2", "action", now.plusSeconds(1))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.NEUTRAL);
    }

    @Test
    void sortsFailureFirst() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-S", "t1", "outcome", now,
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "success",
                       ExperienceAttributeKeys.RESULT, "ok")),
            mem("m2", "case-F", "t1", "outcome", now,
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failed",
                       ExperienceAttributeKeys.RESULT, "err"))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.FAILURE);
        assertThat(trajectories.get(1).outcome()).isEqualTo(TrajectoryOutcome.SUCCESS);
    }

    @Test
    void skipsNullCaseId() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", null, "t1", "observation", now),
            mem("m2", "case-A", "t1", "action", now)
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories).hasSize(1);
        assertThat(trajectories.get(0).caseId()).isEqualTo("case-A");
    }

    @Test
    void handlesNullTurnId() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", null, "observation", now)
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories).hasSize(1);
        assertThat(trajectories.get(0).steps().get(0).turnId()).isNull();
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(TrajectoryGrouper.group(List.of())).isEmpty();
    }

    @Test
    void groupsMultipleEventsInSameTurn() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "observation", now),
            mem("m2", "case-A", "t1", "action", now.plusSeconds(1))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).steps()).hasSize(1);
        assertThat(trajectories.get(0).steps().get(0).events()).hasSize(2);
    }
}
