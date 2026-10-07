package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CulturalContextEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    String description,
    List<Norm> norms,
    @JsonProperty("context_properties") Map<String, String> contextProperties
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Norm(String name, Double strength, String description) {}
}
