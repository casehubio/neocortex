package io.casehub.neocortex.memory.seeding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TriggerSpec(
    String node,
    List<Double> intensity,
    String repetition,
    String schedule
) {}
