package io.casehub.neocortex.memory.seeding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogueIndex(
    String version,
    @JsonProperty("caps_topology_version") int capsTopologyVersion,
    @JsonProperty("spec_issue") int specIssue,
    @JsonProperty("parent_epic") int parentEpic
) {}
