package io.casehub.neocortex.cognition.situational;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record PersonalityState(
        Map<String, FacetState> facets,
        Map<SituationalMode, Double> intensities,
        Map<SituationalMode, Boolean> engaged,
        Map<SituationalMode.Group, SituationalMode> winners,
        Instant cursorTime,
        Instant hourCursor,
        Instant exposedUntil
) {
    public static PersonalityState initial(Set<String> facetNames, Instant now) {
        var facets = new LinkedHashMap<String, FacetState>();
        for (String name : facetNames) {
            facets.put(name, FacetState.initial());
        }
        var intensities = new EnumMap<SituationalMode, Double>(SituationalMode.class);
        var engaged = new EnumMap<SituationalMode, Boolean>(SituationalMode.class);
        for (SituationalMode mode : SituationalMode.values()) {
            intensities.put(mode, 0.0);
            engaged.put(mode, false);
        }
        Instant hourStart = truncateToHour(now);
        return new PersonalityState(facets, intensities, engaged, new EnumMap<>(SituationalMode.Group.class),
                now, hourStart, Instant.EPOCH);
    }

    public FacetState facet(String name) {
        return facets.get(name);
    }

    public double intensity(SituationalMode mode) {
        return intensities.getOrDefault(mode, 0.0);
    }

    public boolean isEngaged(SituationalMode mode) {
        return engaged.getOrDefault(mode, false);
    }

    public PersonalityState withCursorTime(Instant t) {
        return new PersonalityState(facets, intensities, engaged, winners, t, hourCursor, exposedUntil);
    }

    public PersonalityState withFacets(Map<String, FacetState> f) {
        return new PersonalityState(f, intensities, engaged, winners, cursorTime, hourCursor, exposedUntil);
    }

    public PersonalityState withHourCursor(Instant hc) {
        return new PersonalityState(facets, intensities, engaged, winners, cursorTime, hc, exposedUntil);
    }

    public PersonalityState withExposedUntil(Instant eu) {
        return new PersonalityState(facets, intensities, engaged, winners, cursorTime, hourCursor, eu);
    }

    static Instant truncateToHour(Instant t) {
        long epochSeconds = t.getEpochSecond();
        long hourSeconds = epochSeconds - (epochSeconds % 3600);
        return Instant.ofEpochSecond(hourSeconds);
    }
}
