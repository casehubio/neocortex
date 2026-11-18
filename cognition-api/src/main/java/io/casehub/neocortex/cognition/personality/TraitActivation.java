package io.casehub.neocortex.cognition.personality;

public record TraitActivation(String functionTerm) {
    public TraitActivation {
        if (functionTerm == null || functionTerm.isBlank()) {
            throw new IllegalArgumentException("functionTerm must not be blank");
        }
    }
}
