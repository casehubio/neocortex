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
    String status,
    String urgency,
    String priority,
    String feasibility,
    @JsonProperty("need_tier") String needTier,
    @JsonProperty("content_domain") String contentDomain,
    @JsonProperty("content_subdomain") String contentSubdomain,
    @JsonProperty("motivation_origin") String motivationOrigin,
    Double clarity,
    @JsonProperty("evidence_basis") String evidenceBasis,
    List<Dependency> dependencies,
    PadValues pad,
    @JsonProperty("sub_goals") List<SubGoal> subGoals,
    List<Association> associations
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dependency(String ref, @JsonProperty("edge_type") String edgeType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubGoal(String id, String tier, String description) {}
}
