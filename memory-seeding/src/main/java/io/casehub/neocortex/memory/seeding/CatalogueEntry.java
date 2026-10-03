package io.casehub.neocortex.memory.seeding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogueEntry(
    String id,
    String model,
    @JsonProperty("clinical_name") String clinicalName,
    String description,
    List<TriggerSpec> triggers,
    List<String> narratives,
    @JsonProperty("expected_outcomes") List<ExpectedOutcome> expectedOutcomes,
    @JsonProperty("developmental_period") String developmentalPeriod,
    @JsonProperty("modulating_axes") Map<String, Object> modulatingAxes,
    List<String> related,
    List<Map<String, String>> sources
) {}
