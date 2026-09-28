package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class TrajectoryGrouper {

    private static final Set<String> FAILURE_STATUSES = Set.of(
        "failed", "failure", "error", "rejected", "timeout");
    private static final Set<String> SUCCESS_STATUSES = Set.of(
        "success", "successful", "completed", "passed", "resolved");

    private TrajectoryGrouper() {}

    public static List<Trajectory> group(List<Memory> sources) {
        if (sources.isEmpty()) return List.of();

        Map<String, List<Memory>> byCase = sources.stream()
            .filter(m -> m.caseId() != null)
            .collect(Collectors.groupingBy(Memory::caseId, LinkedHashMap::new, Collectors.toList()));

        List<Trajectory> trajectories = new ArrayList<>();
        for (var entry : byCase.entrySet()) {
            var caseMemories = entry.getValue().stream()
                .sorted(Comparator.comparing(Memory::createdAt))
                .toList();

            var steps = buildSteps(caseMemories);
            var outcome = classifyOutcome(caseMemories);
            trajectories.add(new Trajectory(entry.getKey(), steps, outcome));
        }

        trajectories.sort(Comparator.comparingInt(t -> switch (t.outcome()) {
            case FAILURE -> 0;
            case NEUTRAL -> 1;
            case SUCCESS -> 2;
        }));
        return trajectories;
    }

    private static List<TrajectoryStep> buildSteps(List<Memory> sorted) {
        Map<String, List<Memory>> byTurn = new LinkedHashMap<>();
        for (var mem : sorted) {
            String turnId = mem.attributes().get(ExperienceAttributeKeys.TURN_ID);
            byTurn.computeIfAbsent(turnId, k -> new ArrayList<>()).add(mem);
        }

        List<TrajectoryStep> steps = new ArrayList<>();
        for (var turnEntry : byTurn.entrySet()) {
            var events = turnEntry.getValue();
            Instant earliest = events.stream()
                .map(Memory::createdAt)
                .min(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
            steps.add(new TrajectoryStep(turnEntry.getKey(), events, earliest));
        }
        steps.sort(Comparator.comparing(TrajectoryStep::timestamp));
        return steps;
    }

    private static TrajectoryOutcome classifyOutcome(List<Memory> memories) {
        boolean hasOutcome = false;
        boolean hasFailure = false;
        boolean hasSuccess = false;

        for (var mem : memories) {
            String eventType = mem.attributes().get(ExperienceAttributeKeys.EVENT_TYPE);
            if (!"outcome".equals(eventType)) continue;

            hasOutcome = true;
            String status = mem.attributes().get(ExperienceAttributeKeys.OUTCOME_STATUS);
            if (status == null) continue;

            String lower = status.toLowerCase();
            if (FAILURE_STATUSES.contains(lower)) hasFailure = true;
            else if (SUCCESS_STATUSES.contains(lower)) hasSuccess = true;
        }

        if (hasFailure) return TrajectoryOutcome.FAILURE;
        if (!hasOutcome) return TrajectoryOutcome.NEUTRAL;
        if (hasSuccess) return TrajectoryOutcome.SUCCESS;
        return TrajectoryOutcome.NEUTRAL;
    }
}
