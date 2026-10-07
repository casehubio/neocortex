package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    @JsonProperty("confidence_origin") String confidenceOrigin,
    String name,
    Map<String, String> properties,
    PadValues pad,
    List<Association> associations
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Association(
        @JsonProperty("entity_ref") String entityRef,
        @JsonProperty("edge_type") String edgeType
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PadValues(Double pleasure, Double arousal, Double dominance) {}
}
