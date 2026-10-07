package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    String name,
    String description,
    @JsonProperty("activity_refs") List<String> activityRefs,
    Map<String, String> properties
) {}
