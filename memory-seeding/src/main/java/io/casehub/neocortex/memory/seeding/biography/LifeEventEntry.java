package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LifeEventEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    @JsonProperty("confidence_origin") String confidenceOrigin,
    String timestamp,
    String description,
    PadValues pad,
    @JsonProperty("sub_thoughts") List<SubThought> subThoughts,
    @JsonProperty("entity_refs") List<String> entityRefs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubThought(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PadValues(Double pleasure, Double arousal, Double dominance) {}
}
