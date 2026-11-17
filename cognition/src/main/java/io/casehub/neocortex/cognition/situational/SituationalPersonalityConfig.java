package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeType;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SituationalPersonalityConfig(
        Map<SituationalMode, ModeDetectionConfig> modes,
        GainsConfig gains,
        DynamicsConfig dynamics,
        Map<String, ClassifierCalibration> classifiers,
        ClassifierCalibration uncalibratedDefault
) {

    public record GainsConfig(double betaN, double betaR, double mFloor, double switchMargin) {
        public GainsConfig {
            if (betaN < 0) throw new IllegalArgumentException("beta_N must be non-negative: " + betaN);
            if (betaR < 0) throw new IllegalArgumentException("beta_R must be non-negative: " + betaR);
        }
    }

    public record ClassifierCalibration(double xFloor, String model, String calibratedOn, String corpus) {
        public ClassifierCalibration {
            if (xFloor < 0.3 || xFloor > 0.9) {
                throw new IllegalArgumentException("x_floor must be in [0.3, 0.9]: " + xFloor);
            }
        }
    }

    public void validate(CapsTopology topology) {
        Set<String> inputNodes = new HashSet<>();
        Set<String> mediatingNodes = new HashSet<>();
        for (var entry : topology.nodes().entrySet()) {
            CapsNode node = entry.getValue();
            if (node.type() == NodeType.INPUT) inputNodes.add(node.id());
            if (node.type() == NodeType.MEDIATING) mediatingNodes.add(node.id());
        }

        for (var entry : modes.entrySet()) {
            SituationalMode mode = entry.getKey();
            ModeDetectionConfig config = entry.getValue();

            validateWeightsSum(mode, "exogenous", config.exogenous());
            validateWeightsSum(mode, "endogenous", config.endogenous());
            validateSignalNodes(mode, config, inputNodes, mediatingNodes);
            validateSignalTransforms(mode, config);
            validateSubFloorHoldMargin(mode, config);
        }
    }

    private void validateWeightsSum(SituationalMode mode, String group, List<SignalConfig> signals) {
        if (signals.isEmpty()) return;
        double sum = signals.stream().mapToDouble(SignalConfig::weight).sum();
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalArgumentException(
                    "%s %s weights sum to %s, expected 1.0".formatted(mode, group, sum));
        }
    }

    private void validateSignalNodes(SituationalMode mode, ModeDetectionConfig config,
                                     Set<String> inputNodes, Set<String> mediatingNodes) {
        for (SignalConfig signal : config.exogenous()) {
            if (signal instanceof SignalConfig.SituationInputSignal s) {
                for (String node : s.nodes()) {
                    if (!inputNodes.contains(node)) {
                        throw new IllegalArgumentException(
                                "%s: situation input node '%s' not in topology input set".formatted(mode, node));
                    }
                }
            }
            if (signal instanceof SignalConfig.CapsMediatingSignal s) {
                for (String node : s.nodes()) {
                    if (!mediatingNodes.contains(node)) {
                        throw new IllegalArgumentException(
                                "%s: mediating node '%s' not in topology mediating set".formatted(mode, node));
                    }
                }
            }
        }
    }

    private void validateSignalTransforms(SituationalMode mode, ModeDetectionConfig config) {
        for (SignalConfig signal : config.exogenous()) {
            if (signal instanceof SignalConfig.SituationInputSignal) {
                // situation input signals must use ABOVE_FLOOR (enforced by YAML parsing)
            }
            if (signal instanceof SignalConfig.CapsMediatingSignal s) {
                if (s.transform() != SignalTransform.RISE_FROM_REST &&
                    s.transform() != SignalTransform.DROP_FROM_REST) {
                    throw new IllegalArgumentException(
                            "%s: mediating signal transform must be RISE_FROM_REST or DROP_FROM_REST, got %s"
                                    .formatted(mode, s.transform()));
                }
            }
        }
    }

    private void validateSubFloorHoldMargin(SituationalMode mode, ModeDetectionConfig config) {
        double maxGain = (1 + gains.betaN()) * (config.relational() != null ? (1 + gains.betaR()) : 1.0);
        double xSub = gains.mFloor() * mediatingWeightFraction(config);
        if (maxGain * xSub >= config.thresholdOff()) {
            throw new IllegalArgumentException(
                    "%s: sub-floor hold margin violated: G(%s) * x_sub(%s) = %s >= threshold_off(%s)"
                            .formatted(mode, maxGain, xSub, maxGain * xSub, config.thresholdOff()));
        }
    }

    private double mediatingWeightFraction(ModeDetectionConfig config) {
        return config.exogenous().stream()
                .filter(s -> s instanceof SignalConfig.CapsMediatingSignal)
                .mapToDouble(SignalConfig::weight)
                .sum();
    }

    public static SituationalPersonalityConfig withDefaults(Map<SituationalMode, ModeDetectionConfig> modes) {
        return new SituationalPersonalityConfig(
                new EnumMap<>(modes),
                new GainsConfig(0.5, 0.25, 0.3, 0.10),
                DynamicsConfig.defaults(),
                Map.of(),
                new ClassifierCalibration(0.6, "uncalibrated", "", "")
        );
    }
}
