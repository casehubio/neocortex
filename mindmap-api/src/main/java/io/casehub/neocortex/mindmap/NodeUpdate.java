package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record NodeUpdate(
        String name,
        Confidence confidence,
        Set<String> traitsToAdd,
        Set<String> traitsToRemove,
        Set<NodeRef> refsToAdd,
        Set<NodeRef> refsToRemove,
        Instant validFrom,
        Instant validUntil,
        Double pleasure,
        Double arousal,
        Double dominance,
        Map<String, String> propertiesToSet,
        Set<String> propertiesToRemove
                        ) {

    public NodeUpdate {
        traitsToAdd        = traitsToAdd == null ? Set.of() : Set.copyOf(traitsToAdd);
        traitsToRemove     = traitsToRemove == null ? Set.of() : Set.copyOf(traitsToRemove);
        refsToAdd          = refsToAdd == null ? Set.of() : Set.copyOf(refsToAdd);
        refsToRemove       = refsToRemove == null ? Set.of() : Set.copyOf(refsToRemove);
        propertiesToSet    = propertiesToSet == null ? Map.of() : Map.copyOf(propertiesToSet);
        propertiesToRemove = propertiesToRemove == null ? Set.of() : Set.copyOf(propertiesToRemove);
    }

    public static NodeUpdate empty() {
        return new NodeUpdate(null, null, Set.of(), Set.of(), Set.of(), Set.of(),
                              null, null, null, null, null, Map.of(), Set.of());
    }

    public NodeUpdate withName(String name) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withConfidence(Confidence confidence) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withTraitsToAdd(Set<String> traitsToAdd) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withTraitsToRemove(Set<String> traitsToRemove) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withRefsToAdd(Set<NodeRef> refsToAdd) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withRefsToRemove(Set<NodeRef> refsToRemove) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withValidFrom(Instant validFrom) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withValidUntil(Instant validUntil) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withPleasure(Double pleasure) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withArousal(Double arousal) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withDominance(Double dominance) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withPad(Double pleasure, Double arousal, Double dominance) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withPropertiesToSet(Map<String, String> propertiesToSet) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }

    public NodeUpdate withPropertiesToRemove(Set<String> propertiesToRemove) {
        return new NodeUpdate(name, confidence, traitsToAdd, traitsToRemove,
                              refsToAdd, refsToRemove, validFrom, validUntil,
                              pleasure, arousal, dominance, propertiesToSet, propertiesToRemove);
    }
}
