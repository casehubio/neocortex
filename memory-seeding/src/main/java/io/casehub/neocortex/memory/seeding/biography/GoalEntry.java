package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoalEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    @JsonProperty("confidence_origin") String confidenceOrigin,
    String name,
    String tier,
    String horizon,
    List<Dependency> dependencies,
    PadValues pad,
    @JsonProperty("sub_goals") List<SubGoal> subGoals
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dependency(String ref, @JsonProperty("edge_type") String edgeType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubGoal(String id, String tier, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PadValues(Double pleasure, Double arousal, Double dominance) {}
}
