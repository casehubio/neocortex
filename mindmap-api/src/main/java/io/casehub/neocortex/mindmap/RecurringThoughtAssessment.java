package io.casehub.neocortex.mindmap;

public record RecurringThoughtAssessment(
        String topicNodeId,
        double thoughtRecurrence,
        double habituationFailure,
        double gapClosureResistance,
        double attentionalCapture,
        double arousalTrajectory,
        double valence,
        double actionTendency,
        double compositeScore,
        ContinuumPosition position) {
}
