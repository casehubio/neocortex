package io.casehub.neocortex.cognition.situational;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SituationalDetector {

    private final SituationalPersonalityConfig config;
    private final String classifierName;

    public SituationalDetector(SituationalPersonalityConfig config) {
        this(config, null);
    }

    public SituationalDetector(SituationalPersonalityConfig config, String classifierName) {
        this.config = config;
        this.classifierName = classifierName;
    }

    public DetectionResult detect(SituationalEvidence evidence, DetectionResult previous, double dtSeconds) {
        var intensities = new EnumMap<SituationalMode, Double>(SituationalMode.class);
        var engaged = new EnumMap<SituationalMode, Boolean>(SituationalMode.class);
        var exoMap = new EnumMap<SituationalMode, Double>(SituationalMode.class);
        var endoMap = new EnumMap<SituationalMode, Double>(SituationalMode.class);
        var relMap = new EnumMap<SituationalMode, Double>(SituationalMode.class);
        var totalMap = new EnumMap<SituationalMode, Double>(SituationalMode.class);

        for (var entry : config.modes().entrySet()) {
            SituationalMode mode = entry.getKey();
            ModeDetectionConfig modeConfig = entry.getValue();

            double X = computeExogenous(modeConfig, mode, evidence);
            double N = computeEndogenous(modeConfig, evidence);
            double R = computeRelational(modeConfig, evidence);

            double E = Math.min(1.0, X * (1.0 + config.gains().betaN() * N) * (1.0 + config.gains().betaR() * R));

            double fadeTauSeconds = modeConfig.fadeTau().toMillis() / 1000.0;
            double prevIntensity = previous.intensity(mode) * Math.exp(-dtSeconds / fadeTauSeconds);
            boolean wasEngaged = previous.isEngaged(mode);

            boolean nowEngaged = wasEngaged;
            double intensity = prevIntensity;

            if (!wasEngaged && E >= modeConfig.thresholdOn()) {
                nowEngaged = true;
            }
            if (nowEngaged) {
                intensity = Math.max(intensity, E);
            }
            if (nowEngaged && intensity < modeConfig.thresholdOff()) {
                nowEngaged = false;
                intensity = 0;
            }

            intensities.put(mode, intensity);
            engaged.put(mode, nowEngaged);
            exoMap.put(mode, X);
            endoMap.put(mode, N);
            relMap.put(mode, R);
            totalMap.put(mode, E);
        }

        var winners = selectGroupWinners(intensities, engaged, previous.winners());

        return new DetectionResult(intensities, engaged, winners, exoMap, endoMap, relMap, totalMap);
    }

    private double computeExogenous(ModeDetectionConfig modeConfig, SituationalMode mode,
                                    SituationalEvidence evidence) {
        if (mode.group() == SituationalMode.Group.SOCIAL && !evidence.hasInterlocutor()) {
            return 0;
        }
        double sum = 0;
        for (SignalConfig signal : modeConfig.exogenous()) {
            sum += signal.weight() * evaluateSignal(signal, evidence);
        }
        return Math.max(0, Math.min(1, sum));
    }

    private double computeEndogenous(ModeDetectionConfig modeConfig, SituationalEvidence evidence) {
        double sum = 0;
        for (SignalConfig signal : modeConfig.endogenous()) {
            double value = evaluateSignal(signal, evidence);
            if (!Double.isNaN(value)) {
                sum += signal.weight() * value;
            }
        }
        return Math.max(0, Math.min(1, sum));
    }

    private double computeRelational(ModeDetectionConfig modeConfig, SituationalEvidence evidence) {
        if (modeConfig.relational() == null) return 0;
        if (!evidence.hasInterlocutor()) return 0;
        double trust = evidence.relationalTrust();
        if (Double.isNaN(trust)) return 0;
        return Math.max(0, Math.min(1, modeConfig.relational().transform().apply(trust)));
    }

    private double evaluateSignal(SignalConfig signal, SituationalEvidence evidence) {
        return switch (signal) {
            case SignalConfig.SituationInputSignal s -> {
                double xFloor = resolveXFloor();
                double agg = aggregate(s.nodes(), evidence.situationInputActivations(), s.aggregation());
                yield SignalTransform.ABOVE_FLOOR.applyWithFloor(agg, xFloor);
            }
            case SignalConfig.CapsMediatingSignal s -> {
                double agg = aggregate(s.nodes(), evidence.capsMediating(), s.aggregation());
                double rest = aggregate(s.nodes(), evidence.capsRestReference(), s.aggregation());
                yield s.transform().applyFromRest(agg, rest, 1.0, 0.0);
            }
            case SignalConfig.SecSignal s -> {
                double product = 1.0;
                for (SignalConfig.SecFactor factor : s.product()) {
                    Double dimValue = evidence.secDimensions().get(factor.dimension());
                    if (dimValue == null) yield 0.0;
                    product *= factor.transform().apply(dimValue);
                }
                yield Math.max(0, Math.min(1, product));
            }
            case SignalConfig.MoodSignal s -> {
                double moodValue = moodAxis(s.axis(), evidence);
                yield Math.max(0, Math.min(1, s.transform().apply(moodValue)));
            }
            case SignalConfig.MoodBandSignal s -> {
                double moodValue = moodAxis(s.axis(), evidence);
                yield Math.max(0, Math.min(1, s.band().apply(moodValue)));
            }
        };
    }

    private double aggregate(List<String> nodes, Map<String, Double> activations, SignalConfig.Aggregation agg) {
        if (nodes.isEmpty()) return 0;
        double result = agg == SignalConfig.Aggregation.MAX ? Double.NEGATIVE_INFINITY : 0;
        int count = 0;
        for (String node : nodes) {
            double val = activations.getOrDefault(node, 0.0);
            if (agg == SignalConfig.Aggregation.MAX) {
                result = Math.max(result, val);
            } else {
                result += val;
            }
            count++;
        }
        if (agg == SignalConfig.Aggregation.MEAN && count > 0) result /= count;
        if (result == Double.NEGATIVE_INFINITY) result = 0;
        return result;
    }

    private double moodAxis(String axis, SituationalEvidence evidence) {
        return switch (axis) {
            case "pleasure" -> evidence.moodPleasure();
            case "arousal" -> evidence.moodArousal();
            case "dominance" -> evidence.moodDominance();
            default -> 0;
        };
    }

    private double resolveXFloor() {
        if (classifierName != null && config.classifiers().containsKey(classifierName)) {
            return config.classifiers().get(classifierName).xFloor();
        }
        return config.uncalibratedDefault().xFloor();
    }

    private EnumMap<SituationalMode.Group, SituationalMode> selectGroupWinners(
            Map<SituationalMode, Double> intensities,
            Map<SituationalMode, Boolean> engaged,
            Map<SituationalMode.Group, SituationalMode> previousWinners) {

        var winners = new EnumMap<SituationalMode.Group, SituationalMode>(SituationalMode.Group.class);

        for (SituationalMode.Group group : SituationalMode.Group.values()) {
            SituationalMode incumbent = previousWinners.get(group);
            SituationalMode best = null;
            double bestIntensity = 0;

            for (SituationalMode mode : SituationalMode.values()) {
                if (mode.group() != group) continue;
                if (!engaged.getOrDefault(mode, false)) continue;
                double intensity = intensities.getOrDefault(mode, 0.0);
                if (intensity > bestIntensity) {
                    best = mode;
                    bestIntensity = intensity;
                }
            }

            if (best == null) continue;

            if (incumbent != null && engaged.getOrDefault(incumbent, false)) {
                double incumbentIntensity = intensities.getOrDefault(incumbent, 0.0);
                if (best != incumbent && bestIntensity < incumbentIntensity + config.gains().switchMargin()) {
                    best = incumbent;
                }
            }

            winners.put(group, best);
        }

        return winners;
    }
}
