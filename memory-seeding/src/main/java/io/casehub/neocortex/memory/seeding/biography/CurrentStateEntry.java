package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentStateEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    String timestamp,
    PadValues mood,
    @JsonProperty("active_goals") List<String> activeGoals,
    @JsonProperty("recent_context") String recentContext
) {}
