package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class PersonalityStateIntegrator {

    private final DynamicsConfig config;
    private final @Nullable CognitiveDefaultsRegistry cognitiveDefaultsRegistry;


    public PersonalityStateIntegrator(DynamicsConfig config) {
        this.config                    = config;
        this.cognitiveDefaultsRegistry = null;
    }

    public PersonalityStateIntegrator(DynamicsConfig config,
                                      CognitiveDefaultsRegistry cognitiveDefaultsRegistry) {
        this.config                    = config;
        this.cognitiveDefaultsRegistry = cognitiveDefaultsRegistry;
    }


    public PersonalityState advance(PersonalityState state, Instant to) {
        var facets = new LinkedHashMap<>(state.facets());
        Instant cursor = state.cursorTime();
        Instant hourCursor = state.hourCursor();
        Instant exposedUntil = state.exposedUntil();

        while (cursor.isBefore(to)) {
            Instant nextHourBoundary = hourCursor.plus(Duration.ofHours(1));
            Instant segEnd = min(to, nextHourBoundary);
            if (cursor.isBefore(exposedUntil) && exposedUntil.isBefore(segEnd)) {
                segEnd = exposedUntil;
            }

            double dt = Duration.between(cursor, segEnd).toMillis() / 1000.0;
            if (dt <= 0) break;

            double weight = cursor.isBefore(exposedUntil) ? 1.0 : config.wTail();
            boolean allSmall = true;

            for (var entry : facets.entrySet()) {
                FacetState fs = entry.getValue();
                double d = fs.displacement();
                double r = fs.resilience();
                double k = r / tauReturnSeconds();

                if (Math.abs(d) < 1e-6) {
                    entry.setValue(fs.withExposureAndLoad(fs.exposure(), fs.load()));
                    continue;
                }
                allSmall = false;

                double expDecay = Math.exp(-k * dt);
                double dIntegralSeconds = d * (1 - expDecay) / k;
                double dIntegralHours = dIntegralSeconds / 3600.0;
                double newExposure = fs.exposure() + weight * dIntegralHours;
                double newLoad = fs.load() + weight * Math.abs(dIntegralHours);
                double newD = d * expDecay;

                entry.setValue(new FacetState(newD, fs.homeOffset(), r, newExposure, newLoad,
                        fs.promoted(), fs.saturatedSince(), fs.cappedSince()));
            }

            cursor = segEnd;

            if (segEnd.equals(nextHourBoundary)) {
                facets = closeHour(facets);
                hourCursor = nextHourBoundary;

                if (allSmall) {
                    long remainingHours = Duration.between(cursor, to).toHours();
                    if (remainingHours > 0) {
                        facets = fastForwardIdleHours(facets, remainingHours);
                        hourCursor = hourCursor.plus(Duration.ofHours(remainingHours));
                        cursor = hourCursor;
                    }
                }
            }
        }

        return state.withFacets(facets).withCursorTime(cursor).withHourCursor(hourCursor).withExposedUntil(exposedUntil);
    }


    public PersonalityState pull(PersonalityState state, Map<String, Double> compositeDeltas,
                                 double maxIntensity, boolean evidenceSustained, Instant now) {
        return pull(state, compositeDeltas, maxIntensity, evidenceSustained, now, 1.0);
    }

    public PersonalityState pull(PersonalityState state, Map<String, Double> compositeDeltas,
                                 double maxIntensity, boolean evidenceSustained, Instant now,
                                 double dt) {
        double effectiveAlpha = dt > 0 ? 1.0 - Math.pow(1.0 - config.alphaPull(), dt) : config.alphaPull();
        var    facets         = new LinkedHashMap<>(state.facets());
        for (var entry : facets.entrySet()) {
            String     facetName = entry.getKey();
            FacetState fs        = entry.getValue();
            double     delta     = compositeDeltas.getOrDefault(facetName, 0.0);
            double     d         = fs.displacement();
            double     newD      = d + effectiveAlpha * (delta - d);
            newD = clampDisplacement(newD, fs.homeOffset(), baselineFor(facetName));
            entry.setValue(fs.withDisplacement(newD));
        }
        Instant exposedUntil = state.exposedUntil();
        if (evidenceSustained) {
            Instant newExposedUntil = now.plus(config.hold());
            exposedUntil = max(exposedUntil, newExposedUntil);
        }
        return state.withFacets(facets).withExposedUntil(exposedUntil);
    }

    public record PromotionCandidate(String facet, double delta) {}

    public Map<String, PromotionCandidate> promotionCandidates(PersonalityState state,
                                                               Map<String, Double> baselines,
                                                               Instant now) {
        var    candidates = new LinkedHashMap<String, PromotionCandidate>();
        double threshold  = config.promoteFraction() * config.dMax();
        for (var entry : state.facets().entrySet()) {
            var facetName = entry.getKey();
            var fs        = entry.getValue();
            if (fs.saturatedSince() == null) {continue;}
            if (Duration.between(fs.saturatedSince(), now).compareTo(config.promoteDwell()) < 0) {continue;}
            double baseline = baselines.getOrDefault(facetName, 0.5);
            if (Math.abs(fs.promoted() + fs.homeOffset()) > config.promotionBudget()) {continue;}
            double delta = clamp01(baseline + fs.promoted() + fs.homeOffset()) - (baseline + fs.promoted());
            if (Math.abs(delta) < 1e-9) {continue;}
            candidates.put(facetName, new PromotionCandidate(facetName, delta));
        }
        return Map.copyOf(candidates);
    }


    private LinkedHashMap<String, FacetState> closeHour(LinkedHashMap<String, FacetState> facets) {
        var    result    = new LinkedHashMap<String, FacetState>();
        double threshold = config.promoteFraction() * config.dMax();
        for (var entry : facets.entrySet()) {
            FacetState fs = entry.getValue();
            double     r  = fs.resilience();
            double     L  = fs.load();
            double     A  = fs.exposure();
            double     o  = fs.homeOffset();

            r = Math.max(config.rMin(), r - L / config.tauFatigue());
            r = 1.0 - (1.0 - r) * Math.exp(-3600.0 / tauHealSeconds());

            double step = (1.0 - r) * A / config.tauDrift();
            if (Math.signum(step) == Math.signum(o)) {
                step = step * (1.0 - Math.abs(o) / config.dMax());
            }
            o = clamp(o + step, -config.dMax(), config.dMax());
            o = o * Math.exp(-3600.0 / tauSetpointSeconds());

            Instant saturatedSince = fs.saturatedSince();
            if (Math.abs(o) >= threshold && saturatedSince == null) {
                saturatedSince = Instant.now();
            } else if (Math.abs(o) < threshold) {
                saturatedSince = null;
            }

            result.put(entry.getKey(), new FacetState(fs.displacement(), o, r, 0, 0,
                                                      fs.promoted(), saturatedSince, fs.cappedSince()));
        }
        return result;
    }

    private LinkedHashMap<String, FacetState> fastForwardIdleHours(LinkedHashMap<String, FacetState> facets, long hours) {
        var    result    = new LinkedHashMap<String, FacetState>();
        double threshold = config.promoteFraction() * config.dMax();
        for (var entry : facets.entrySet()) {
            FacetState fs = entry.getValue();
            double     r  = 1.0 - (1.0 - fs.resilience()) * Math.exp(-hours * 3600.0 / tauHealSeconds());
            double     o  = fs.homeOffset() * Math.exp(-hours * 3600.0 / tauSetpointSeconds());

            Instant saturatedSince = fs.saturatedSince();
            if (Math.abs(o) < threshold) {
                saturatedSince = null;
            }

            result.put(entry.getKey(), new FacetState(fs.displacement(), o, r, 0, 0,
                                                      fs.promoted(), saturatedSince, fs.cappedSince()));
        }
        return result;
    }

    private double clampDisplacement(double d, double homeOffset, double baseline) {
        double home = clamp01(baseline + homeOffset);
        double c = home + d;
        c = clamp01(c);
        return c - home;
    }

    private double baselineFor(String facetName) {
        return 0.5;
    }

    private double tauReturnSeconds() { return config.tauReturn().toMillis() / 1000.0; }
    private double tauHealSeconds() { return config.tauHeal().toMillis() / 1000.0; }
    private double tauSetpointSeconds() { return config.tauSetpoint().toMillis() / 1000.0; }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
