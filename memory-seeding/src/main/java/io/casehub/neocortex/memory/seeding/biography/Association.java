package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Association(
    @JsonProperty("entity_ref") String entityRef,
    @JsonProperty("edge_type") String edgeType,
    Double confidence
) {}
