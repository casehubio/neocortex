package io.casehub.neocortex.caps.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.neocortex.caps.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

public class CapsTopologyLoader {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public CapsTopology loadFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + resource);
            return parse(mapper.readTree(is));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load CAPS topology from " + resource, e);
        }
    }

    CapsTopology parse(JsonNode root) {
        int version = root.path("version").asInt(1);
        Map<String, CapsNode> nodes = parseNodes(root.path("nodes"));
        List<CapsConnection> connections = parseConnections(root.path("connections"));
        Map<String, DispositionModifier> modifiers = parseDispositionModifiers(
            root.path("disposition_modifiers"));
        List<DistortionDefinition> distortions = parseDistortions(root.path("distortions"));
        WeightUpdateParameters weightUpdate = parseWeightUpdate(root);
        return new CapsTopology(version, nodes, connections, modifiers, distortions, weightUpdate);
    }

    private Map<String, CapsNode> parseNodes(JsonNode nodesNode) {
        Map<String, CapsNode> result = new LinkedHashMap<>();
        parseNodeGroup(nodesNode.path("input"), NodeType.INPUT, result);
        parseNodeGroup(nodesNode.path("mediating"), NodeType.MEDIATING, result);
        parseNodeGroup(nodesNode.path("output"), NodeType.OUTPUT, result);
        return Collections.unmodifiableMap(result);
    }

    private void parseNodeGroup(JsonNode groupNode, NodeType type,
                                 Map<String, CapsNode> result) {
        groupNode.fields().forEachRemaining(entry -> {
            String category = entry.getKey();
            JsonNode categoryNodes = entry.getValue();
            if (categoryNodes.isArray()) {
                for (JsonNode nodeEntry : categoryNodes) {
                    if (nodeEntry.isTextual()) {
                        result.put(nodeEntry.asText(), new CapsNode(
                            nodeEntry.asText(), type,
                            defaultRange(type), category,
                            List.of(), List.of()));
                    } else if (nodeEntry.isObject()) {
                        String id = nodeEntry.has("id")
                            ? nodeEntry.path("id").asText()
                            : nodeEntry.path("name").asText();
                        NodeRange range = parseRange(nodeEntry, type);
                        List<String> sourceModels = parseStringList(nodeEntry.path("source_models"));
                        List<String> keywords = parseStringList(nodeEntry.path("keywords"));
                        result.put(id, new CapsNode(id, type, range, category,
                            sourceModels, keywords));
                    }
                }
            }
        });
    }

    private NodeRange parseRange(JsonNode node, NodeType type) {
        if (node.has("type")) {
            String typeStr = node.path("type").asText();
            if ("bipolar".equalsIgnoreCase(typeStr)) return NodeRange.BIPOLAR;
            if ("unipolar".equalsIgnoreCase(typeStr)) return NodeRange.UNIPOLAR;
        }
        if (node.has("range")) {
            JsonNode rangeNode = node.path("range");
            if (rangeNode.isArray() && rangeNode.size() == 2) {
                double min = rangeNode.get(0).asDouble();
                if (min < 0) return NodeRange.BIPOLAR;
            }
        }
        return defaultRange(type);
    }

    private NodeRange defaultRange(NodeType type) {
        return NodeRange.UNIPOLAR;
    }

    private List<CapsConnection> parseConnections(JsonNode connectionsNode) {
        if (!connectionsNode.isArray()) return List.of();
        List<CapsConnection> result = new ArrayList<>();
        for (JsonNode conn : connectionsNode) {
            String from = conn.path("from").asText();
            String to = conn.path("to").asText();
            String id = from + "__" + to;
            double weight = conn.path("weight").asDouble(0.5);
            WeightProvenance provenance = parseProvenance(conn.path("provenance").asText("estimated"));
            String source = conn.path("source").asText("");
            List<String> tags = parseStringList(conn.path("tags"));
            result.add(new CapsConnection(id, from, to, weight, provenance, source, tags));
        }
        return Collections.unmodifiableList(result);
    }

    private WeightProvenance parseProvenance(String text) {
        return switch (text.toLowerCase()) {
            case "empirical" -> WeightProvenance.EMPIRICAL;
            case "consensus" -> WeightProvenance.CONSENSUS;
            default -> WeightProvenance.ESTIMATED;
        };
    }

    private Map<String, DispositionModifier> parseDispositionModifiers(JsonNode modifiersNode) {
        if (modifiersNode.isMissingNode()) return Map.of();
        Map<String, DispositionModifier> result = new LinkedHashMap<>();
        modifiersNode.fields().forEachRemaining(axisEntry -> {
            String axisName = axisEntry.getKey();
            JsonNode axisValues = axisEntry.getValue();
            Map<String, Map<String, Double>> valueModifiers = new LinkedHashMap<>();
            axisValues.fields().forEachRemaining(valueEntry -> {
                String axisValue = valueEntry.getKey();
                JsonNode modifiers = valueEntry.getValue();
                Map<String, Double> modMap = new LinkedHashMap<>();
                modifiers.fields().forEachRemaining(modEntry ->
                    modMap.put(modEntry.getKey(), modEntry.getValue().asDouble(1.0)));
                valueModifiers.put(axisValue, Collections.unmodifiableMap(modMap));
            });
            result.put(axisName, new DispositionModifier(
                Collections.unmodifiableMap(valueModifiers)));
        });
        return Collections.unmodifiableMap(result);
    }

    private List<DistortionDefinition> parseDistortions(JsonNode distortionsNode) {
        if (!distortionsNode.isArray()) return List.of();
        List<DistortionDefinition> result = new ArrayList<>();
        for (JsonNode d : distortionsNode) {
            result.add(new DistortionDefinition(
                d.path("id").asText(),
                d.path("baseThreshold").asDouble(0.5),
                d.path("multiplierMin").asDouble(1.0),
                d.path("multiplierMax").asDouble(1.0),
                "ADDITIVE".equalsIgnoreCase(d.path("effect").asText("MULTIPLICATIVE"))
                    ? DistortionEffect.ADDITIVE : DistortionEffect.MULTIPLICATIVE,
                d.path("negativeWeight").asDouble(1.0),
                d.path("positiveWeight").asDouble(0.3),
                parseStringList(d.path("targetCategories"))
            ));
        }
        return Collections.unmodifiableList(result);
    }

    private WeightUpdateParameters parseWeightUpdate(JsonNode root) {
        JsonNode rw = root.path("weight_update").path("rescorla_wagner");
        JsonNode conv = root.path("weight_update").path("convergence");
        JsonNode schedNode = root.path("weight_update").path("schedule_modifiers");

        double[] alphaRange = parseDoubleArray(rw.path("alpha_range"), new double[]{0.01, 0.25});
        double[] betaRange = parseDoubleArray(rw.path("beta_range"), new double[]{0.1, 0.5});

        Map<String, Double> scheduleModifiers = new LinkedHashMap<>();
        if (schedNode.isObject()) {
            schedNode.fields().forEachRemaining(e ->
                scheduleModifiers.put(e.getKey(), e.getValue().asDouble(1.0)));
        }

        double[] effectiveWeightCap = parseDoubleArray(
            conv.path("effective_weight_cap"), new double[]{-2.0, 2.0});
        double[] compoundDistortionCap = parseDoubleArray(
            conv.path("compound_distortion_cap"), new double[]{0.1, 3.0});

        return new WeightUpdateParameters(
            alphaRange[0], alphaRange[1],
            betaRange[0], betaRange[1],
            rw.path("temporal_discount").asDouble(0.95),
            Collections.unmodifiableMap(scheduleModifiers),
            root.path("weight_update").path("vicarious_discount").asDouble(0.20),
            conv.path("max_iterations").asInt(100),
            conv.path("epsilon").asDouble(0.001),
            effectiveWeightCap,
            compoundDistortionCap,
            conv.path("saturation_warning_threshold").asDouble(0.60)
        );
    }

    private double[] parseDoubleArray(JsonNode node, double[] defaults) {
        if (!node.isArray()) return defaults;
        double[] result = new double[node.size()];
        for (int i = 0; i < node.size(); i++) {
            result[i] = node.get(i).asDouble();
        }
        return result;
    }

    private List<String> parseStringList(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item.asText());
        }
        return Collections.unmodifiableList(result);
    }
}
