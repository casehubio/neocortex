package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DispositionProfileEntry(
    String id,
    @JsonProperty("entity_ref") String entityRef,
    @JsonProperty("social_orient") String socialOrient,
    @JsonProperty("rule_following") String ruleFollowing,
    @JsonProperty("risk_appetite") String riskAppetite,
    String autonomy,
    @JsonProperty("conflict_mode") String conflictMode,
    @JsonProperty("disposition_profile") List<WeightedTerm> dispositionProfile
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeightedTerm(String function, Double weight) {}
}
