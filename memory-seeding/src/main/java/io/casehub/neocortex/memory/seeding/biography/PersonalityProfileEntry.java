package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonalityProfileEntry(
    String id,
    @JsonProperty("entity_ref") String entityRef,
    Frameworks frameworks,
    List<ArchetypeSelection> archetypes,
    Disposition disposition,
    Reconciliation reconciliation,
    List<String> inconsistencies
) {
    public PersonalityProfileEntry {
        archetypes = archetypes != null ? List.copyOf(archetypes) : List.of();
        inconsistencies = inconsistencies != null ? List.copyOf(inconsistencies) : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Frameworks(
        OceanAssessment ocean,
        MbtiAssessment mbti,
        @JsonProperty("jungian_stack") List<JungianFunction> jungianStack,
        EnneagramAssessment enneagram,
        DiscAssessment disc,
        BelbinAssessment belbin,
        @JsonProperty("thomas_kilmann") ThomasKilmannAssessment thomasKilmann,
        SdiAssessment sdi,
        AmpdAssessment ampd,
        AttachmentAssessment attachment,
        @JsonProperty("locus_of_control") LocusAssessment locusOfControl
    ) {
        public Frameworks {
            jungianStack = jungianStack != null ? List.copyOf(jungianStack) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OceanAssessment(
        Double openness, Double conscientiousness, Double extraversion,
        Double agreeableness, Double neuroticism,
        String evidence, Double confidence, String source
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MbtiAssessment(String type, String evidence, Double confidence, String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JungianFunction(
        @JsonProperty("function") String functionName,
        Double weight, String evidence
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnneagramAssessment(String type, String wing, String evidence, Double confidence, String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscAssessment(String primary, String secondary, String evidence, Double confidence, String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BelbinAssessment(String primary, String secondary, String evidence, Double confidence, String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ThomasKilmannAssessment(String primary, String evidence, Double confidence, String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SdiAssessment(String primary, String evidence, Double confidence, String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmpdAssessment(
        Integer callousness, Integer manipulativeness, Integer grandiosity,
        Integer deceitfulness, @JsonProperty("attention-seeking") Integer attentionSeeking,
        Integer hostility, Integer suspiciousness,
        @JsonProperty("intimacy-avoidance") Integer intimacyAvoidance,
        @JsonProperty("emotional-lability") Integer emotionalLability,
        Integer anxiousness, @JsonProperty("risk-taking") Integer riskTaking,
        Integer impulsivity, Integer withdrawal,
        String evidence, Double confidence, String source
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttachmentAssessment(
        String style, Double anxiety, Double avoidance,
        String evidence, Double confidence, String source
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocusAssessment(
        String general, Map<String, String> domains,
        String evidence, Double confidence, String source
    ) {
        public LocusAssessment {
            domains = domains != null ? Map.copyOf(domains) : Map.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArchetypeSelection(
        Integer rank, String family,
        @JsonProperty("sub_archetype") String subArchetype,
        List<String> adjectives,
        String evidence, Double confidence
    ) {
        public ArchetypeSelection {
            adjectives = adjectives != null ? List.copyOf(adjectives) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Disposition(
        DispositionAxis socialOrient,
        DispositionAxis ruleFollowing,
        DispositionAxis riskAppetite,
        DispositionAxis autonomy,
        DispositionAxis conflictMode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DispositionAxis(Double score, String term, String justification) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reconciliation(
        @JsonProperty("published_sources") List<String> publishedSources,
        List<Disagreement> disagreements
    ) {
        public Reconciliation {
            publishedSources = publishedSources != null ? List.copyOf(publishedSources) : List.of();
            disagreements = disagreements != null ? List.copyOf(disagreements) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Disagreement(
        String framework,
        String extracted,
        String published,
        String axis,
        @JsonProperty("evidence_for_extracted") String evidenceForExtracted,
        @JsonProperty("evidence_for_published") String evidenceForPublished,
        String resolution
    ) {}
}
