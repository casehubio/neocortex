package io.casehub.neocortex.cognition.prompt.observation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RegulationTracker {

    public enum Trajectory { IMPROVING, STABLE, WORSENING }

    public record TrajectoryResult(Trajectory trajectory, double delta) {}

    private static final double THRESHOLD = 0.05;
    private static final int DISTRESSED_INDEX = 2;

    private final Map<String, List<double[]>> history = new HashMap<>();

    public void recordPosterior(String targetId, double[] posterior) {
        history.computeIfAbsent(targetId, k -> new ArrayList<>()).add(posterior.clone());
    }

    public Optional<TrajectoryResult> computeTrajectory(String targetId) {
        var entries = history.get(targetId);
        if (entries == null || entries.size() < 2) { return Optional.empty(); }
        double prev = entries.get(entries.size() - 2)[DISTRESSED_INDEX];
        double curr = entries.get(entries.size() - 1)[DISTRESSED_INDEX];
        double delta = curr - prev;
        Trajectory t = delta < -THRESHOLD ? Trajectory.IMPROVING
                     : delta > THRESHOLD ? Trajectory.WORSENING
                     : Trajectory.STABLE;
        return Optional.of(new TrajectoryResult(t, delta));
    }
}
