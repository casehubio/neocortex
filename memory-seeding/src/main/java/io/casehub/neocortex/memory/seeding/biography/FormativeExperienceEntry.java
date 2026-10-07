package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormativeExperienceEntry(
    String id,
    @JsonProperty("source_ref") String sourceRef,
    @JsonProperty("catalogue_entry_id") String catalogueEntryId,
    @JsonProperty("intensity_override") Double intensityOverride,
    @JsonProperty("repetition_override") Integer repetitionOverride
) {}
