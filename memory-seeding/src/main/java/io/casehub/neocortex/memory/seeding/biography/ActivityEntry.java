package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    String name,
    @JsonProperty("activity_type") String activityType,
    String date,
    @JsonProperty("place_ref") String placeRef,
    List<Participant> participants,
    String notes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(
        @JsonProperty("entity_ref") String entityRef,
        String role
    ) {}
}
