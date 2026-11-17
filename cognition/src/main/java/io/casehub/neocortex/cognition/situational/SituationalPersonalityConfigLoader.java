package io.casehub.neocortex.cognition.situational;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SituationalPersonalityConfigLoader {

    private static final ObjectMapper MAPPER = YamlMappers.create();

    public SituationalPersonalityConfig loadFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + resource);
            return load(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public SituationalPersonalityConfig load(InputStream is) {
        try {
            JsonNode root = MAPPER.readTree(is);
            return parse(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    SituationalPersonalityConfig parse(JsonNode root) {
        var gainsNode = root.get("gains");
        var gains = new SituationalPersonalityConfig.GainsConfig(
                gainsNode.get("beta_N").asDouble(),
                gainsNode.get("beta_R").asDouble(),
                gainsNode.get("m_floor").asDouble(),
                gainsNode.get("switch_margin").asDouble()
        );

        var classifiers = parseClassifiers(root.get("classifiers"));
        var uncalibrated = classifiers.getOrDefault("uncalibrated_default",
                new SituationalPersonalityConfig.ClassifierCalibration(0.6, "uncalibrated", "", ""));
        classifiers.remove("uncalibrated_default");

        var modes = parseModes(root.get("modes"));
        var dynamics = parseDynamics(root);

        return new SituationalPersonalityConfig(modes, gains, dynamics, classifiers, uncalibrated);
    }

    private Map<SituationalMode, ModeDetectionConfig> parseModes(JsonNode modesNode) {
        var modes = new EnumMap<SituationalMode, ModeDetectionConfig>(SituationalMode.class);
        Iterator<Map.Entry<String, JsonNode>> fields = modesNode.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            SituationalMode mode;
            try {
                mode = SituationalMode.valueOf(entry.getKey().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown situational mode '%s' in YAML config".formatted(entry.getKey()), e);
            }
            modes.put(mode, parseModeConfig(mode, entry.getValue()));
        }
        return modes;
    }

    private ModeDetectionConfig parseModeConfig(SituationalMode mode, JsonNode node) {
        var exogenous = parseSignals(node.get("exogenous"));
        var endogenous = parseSignals(node.get("endogenous"));
        SignalConfig.RelationalConfig relational = null;
        if (node.has("relational") && !node.get("relational").asText().equals("none")) {
            var relNode = node.get("relational");
            relational = new SignalConfig.RelationalConfig(
                    relNode.get("field").asText(),
                    SignalTransform.valueOf(relNode.get("transform").asText().toUpperCase())
            );
        }
        return new ModeDetectionConfig(
                mode.group(),
                exogenous,
                endogenous,
                relational,
                node.get("threshold_on").asDouble(),
                node.get("threshold_off").asDouble(),
                Duration.parse(node.get("fade_tau").asText())
        );
    }

    private List<SignalConfig> parseSignals(JsonNode signalsNode) {
        var signals = new ArrayList<SignalConfig>();
        if (signalsNode == null || signalsNode.isNull()) return signals;
        for (JsonNode s : signalsNode) {
            signals.add(parseSignal(s));
        }
        return signals;
    }

    private SignalConfig parseSignal(JsonNode s) {
        String source = s.get("source").asText();
        double weight = s.get("weight").asDouble();
        return switch (source) {
            case "situation_input" -> new SignalConfig.SituationInputSignal(
                    parseStringList(s.get("nodes")),
                    parseAggregation(s),
                    weight
            );
            case "caps_mediating" -> new SignalConfig.CapsMediatingSignal(
                    parseStringList(s.has("nodes") ? s.get("nodes") : MAPPER.createArrayNode().add(s.get("node"))),
                    parseAggregation(s),
                    SignalTransform.valueOf(s.get("transform").asText().toUpperCase()),
                    weight
            );
            case "sec" -> new SignalConfig.SecSignal(
                    parseSecProduct(s.get("product")),
                    weight
            );
            case "mood" -> {
                if (s.has("transform")) {
                    String transformText = s.get("transform").asText();
                    if (transformText.startsWith("band(")) {
                        var params = transformText.substring(5, transformText.length() - 1).split(",");
                        yield new SignalConfig.MoodBandSignal(
                                s.get("axis").asText(),
                                new ParameterisedTransform.Band(
                                        Double.parseDouble(params[0].trim()),
                                        Double.parseDouble(params[1].trim())
                                ),
                                weight
                        );
                    }
                    yield new SignalConfig.MoodSignal(
                            s.get("axis").asText(),
                            SignalTransform.valueOf(transformText.toUpperCase()),
                            weight
                    );
                }
                yield new SignalConfig.MoodSignal(s.get("axis").asText(), SignalTransform.IDENTITY, weight);
            }
            default -> throw new IllegalArgumentException("Unknown signal source: " + source);
        };
    }

    private List<SignalConfig.SecFactor> parseSecProduct(JsonNode productNode) {
        var factors = new ArrayList<SignalConfig.SecFactor>();
        for (JsonNode f : productNode) {
            if (f.has("dim")) {
                SignalTransform transform = f.has("transform")
                        ? SignalTransform.valueOf(f.get("transform").asText().toUpperCase())
                        : SignalTransform.IDENTITY;
                factors.add(new SignalConfig.SecFactor(f.get("dim").asText(), transform));
            }
        }
        return factors;
    }

    private SignalConfig.Aggregation parseAggregation(JsonNode s) {
        if (!s.has("aggregate")) return SignalConfig.Aggregation.MAX;
        return SignalConfig.Aggregation.valueOf(s.get("aggregate").asText().toUpperCase());
    }

    private List<String> parseStringList(JsonNode node) {
        var list = new ArrayList<String>();
        for (JsonNode n : node) list.add(n.asText());
        return list;
    }

    private Map<String, SituationalPersonalityConfig.ClassifierCalibration> parseClassifiers(JsonNode node) {
        var classifiers = new java.util.LinkedHashMap<String, SituationalPersonalityConfig.ClassifierCalibration>();
        if (node == null) return classifiers;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            var n = entry.getValue();
            classifiers.put(entry.getKey(), new SituationalPersonalityConfig.ClassifierCalibration(
                    n.get("x_floor").asDouble(),
                    n.has("model") ? n.get("model").asText() : "",
                    n.has("calibrated_on") ? n.get("calibrated_on").asText() : "",
                    n.has("corpus") ? n.get("corpus").asText() : ""
            ));
        }
        return classifiers;
    }

    private DynamicsConfig parseDynamics(JsonNode root) {
        if (!root.has("dynamics")) return DynamicsConfig.defaults();
        JsonNode d = root.get("dynamics");
        return new DynamicsConfig(
                d.has("alpha_pull") ? d.get("alpha_pull").asDouble() : 0.30,
                d.has("tau_return") ? Duration.parse(d.get("tau_return").asText()) : Duration.ofHours(6),
                d.has("s_max") ? d.get("s_max").asDouble() : 0.25,
                d.has("d_max") ? d.get("d_max").asDouble() : 0.10,
                d.has("tau_fatigue") ? d.get("tau_fatigue").asDouble() : 12.0,
                d.has("tau_heal") ? Duration.parse(d.get("tau_heal").asText()) : Duration.ofDays(30),
                d.has("r_min") ? d.get("r_min").asDouble() : 0.2,
                d.has("tau_drift") ? d.get("tau_drift").asDouble() : 15.0,
                d.has("w_tail") ? d.get("w_tail").asDouble() : 0.1,
                d.has("hold") ? Duration.parse(d.get("hold").asText()) : Duration.ofMinutes(10),
                d.has("tau_setpoint") ? Duration.parse(d.get("tau_setpoint").asText()) : Duration.ofDays(180),
                d.has("epsilon_publish") ? d.get("epsilon_publish").asDouble() : 0.01
        );
    }
}
