package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BeliefEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    @JsonProperty("confidence_origin") String confidenceOrigin,
    Double confidence,
    String description,
    @JsonProperty("cognitive_kind") String cognitiveKind,
    @JsonProperty("entity_refs") List<String> entityRefs,
    Map<String, String> properties
) {}
