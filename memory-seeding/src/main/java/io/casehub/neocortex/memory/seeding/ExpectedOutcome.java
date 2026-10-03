package io.casehub.neocortex.memory.seeding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpectedOutcome(
    String node,
    String direction,
    List<Double> strength
) {}
