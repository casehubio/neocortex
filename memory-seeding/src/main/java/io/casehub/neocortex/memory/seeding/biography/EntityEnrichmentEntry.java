package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EntityEnrichmentEntry(
        String id,
        @JsonProperty("entity_ref") String entityRef,
        @JsonProperty("drive_baselines") Map<String, DriveBaseline> driveBaselines,
        @JsonProperty("caps_patterns") List<CapsPattern> capsPatterns,
        @JsonProperty("behavioural_attractors") List<BehaviouralAttractor> behaviouralAttractors,
        @JsonProperty("relationship_stages") List<RelationshipStage> relationshipStages,
        @JsonProperty("goal_coherence") GoalCoherence goalCoherence,
        @JsonProperty("identity") IdentityFields identity
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriveBaseline(Double baseline, String justification) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CapsPattern(
            String pattern,
            Double frequency,
            Double confidence,
            @JsonProperty("input_activations") Map<String, Double> inputActivations
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BehaviouralAttractor(String name, String description, String trigger, String response,
                                       Double strength) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelationshipStage(
            @JsonProperty("other_agent") String otherAgent,
            List<Transition> transitions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transition(String from, String to, String period, String trigger) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalCoherence(
            String summary,
            List<GoalRelation> conflicts,
            List<GoalRelation> synergies
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalRelation(List<String> goals, String nature) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdentityFields(
            @JsonProperty("canonical_first") String canonicalFirst,
            @JsonProperty("canonical_last") String canonicalLast,
            @JsonProperty("canonical_title_prefix") String canonicalTitlePrefix,
            @JsonProperty("pronouns_subject") String pronounsSubject,
            @JsonProperty("pronouns_object") String pronounsObject,
            @JsonProperty("pronouns_possessive") String pronounsPossessive,
            @JsonProperty("pronouns_reflexive") String pronounsReflexive,
            @JsonProperty("preferred_short") String preferredShort,
            @JsonProperty("preferred_long") String preferredLong,
            List<String> aliases
    ) {
        public Map<String, String> toProperties() {
            var props = new java.util.HashMap<String, String>();
            if (canonicalFirst != null) {props.put("canonicalFirst", canonicalFirst);}
            if (canonicalLast != null) {props.put("canonicalLast", canonicalLast);}
            if (canonicalTitlePrefix != null) {props.put("canonicalTitlePrefix", canonicalTitlePrefix);}
            if (pronounsSubject != null) {props.put("pronounsSubject", pronounsSubject);}
            if (pronounsObject != null) {props.put("pronounsObject", pronounsObject);}
            if (pronounsPossessive != null) {props.put("pronounsPossessive", pronounsPossessive);}
            if (pronounsReflexive != null) {props.put("pronounsReflexive", pronounsReflexive);}
            if (preferredShort != null) {props.put("preferredShort", preferredShort);}
            if (preferredLong != null) {props.put("preferredLong", preferredLong);}
            return props;
        }
    }
}
