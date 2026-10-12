package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrganisationEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    @JsonProperty("confidence_origin") String confidenceOrigin,
    String name,
    List<String> traits,
    Map<String, String> properties,
    PadValues pad,
    List<Association> associations,
    @JsonProperty("valid_from") String validFrom,
    @JsonProperty("valid_until") String validUntil
) {}
