package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RelationshipEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    @JsonProperty("confidence_origin") String confidenceOrigin,
    String name,
    List<String> traits,
    BdiValues bdi,
    AffectValues affect,
    DynamicsValues dynamics,
    Map<String, String> properties
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdiValues(String beliefs, String desires, String intentions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AffectValues(PadValues pad) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PadValues(Double pleasure, Double arousal, Double dominance) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DynamicsValues(Double trust, @JsonProperty("conflict_mode") String conflictMode) {}
}
