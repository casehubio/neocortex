package io.casehub.neocortex.cognition.innerlife;

record MotivationAssessment(double score, String content, String channelHint) {
    MotivationAssessment {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be in [0.0, 1.0]");
        }
    }
}
