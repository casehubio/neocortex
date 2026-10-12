package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LifeEventEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    @JsonProperty("confidence_origin") String confidenceOrigin,
    String timestamp,
    String description,
    String domain,
    @JsonProperty("event_type") String eventType,
    String subject,
    String period,
    @JsonProperty("related_entities") List<String> relatedEntities,
    PadValues pad,
    @JsonProperty("occ_emotions") List<OccEmotion> occEmotions,
    @JsonProperty("sec_appraisal") SecAppraisal secAppraisal,
    @JsonProperty("action_tendencies") List<ActionTendency> actionTendencies,
    @JsonProperty("cognitive_distortions") List<CognitiveDistortion> cognitiveDistortions,
    @JsonProperty("drive_impact") DriveImpact driveImpact,
    @JsonProperty("caps_inputs") Map<String, Double> capsInputs,
    @JsonProperty("goal_impacts") List<GoalImpact> goalImpacts,
    Habituation habituation,
    @JsonProperty("gut_feeling") GutFeeling gutFeeling,
    @JsonProperty("sub_thoughts") List<SubThought> subThoughts,
    @JsonProperty("entity_refs") List<String> entityRefs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubThought(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OccEmotion(String type, Double intensity, String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecAppraisal(Double relevance, Double novelty, Double urgency,
        Double conduciveness, Double controllability, Double adjustability,
        @JsonProperty("internal_standards") Double internalStandards,
        @JsonProperty("external_standards") Double externalStandards) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionTendency(String type, Double intensity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CognitiveDistortion(String type, Double strength) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriveImpact(Double curiosity, Double competence,
        Double affiliation, Double autonomy) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalImpact(String goal, String relationship,
        @JsonProperty("urgency_delta") Double urgencyDelta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Habituation(String novelty,
        @JsonProperty("habituation_score") Double habituationScore,
        String echoes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GutFeeling(String valence, Double intensity, String resonance) {}
}
