package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.intelligence.TypeRegistry;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(25)
public class SchemaDiscoveryPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(SchemaDiscoveryPhase.class.getName());
    private static final String SCHEMA_PREFIX = "schema.";
    private static final Set<String> SKIP_PROPERTIES = Set.of("cognitiveKind");
    private static final int MAX_ENUM_VALUES = 10;

    private final MindMapStore store;
    private final TypeRegistry registry;
    private final double threshold;
    private final int minSamples;
    private final double requiredThreshold;

    @Inject
    public SchemaDiscoveryPhase(MindMapStore store,
                                 Instance<TypeRegistry> registry,
                                 Instance<SchemaDiscoveryConfig> config) {
        this.store = store;
        this.registry = registry.isResolvable() ? registry.get() : null;
        SchemaDiscoveryConfig c = config.isResolvable() ? config.get() : null;
        this.threshold = c != null ? c.threshold() : 0.8;
        this.minSamples = c != null ? c.minSamples() : 5;
        this.requiredThreshold = c != null ? c.requiredThreshold() : 0.95;
    }

    SchemaDiscoveryPhase(MindMapStore store, TypeRegistry registry,
                          double threshold, int minSamples, double requiredThreshold) {
        this.store = store;
        this.registry = registry;
        this.threshold = threshold;
        this.minSamples = minSamples;
        this.requiredThreshold = requiredThreshold;
    }

    @Override
    public String name() {
        return "schema-discovery";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (registry == null) return;
        for (String sgType : subgraphPriority) {
            discoverForSubgraphType(sgType, tenantId);
        }
    }

    private void discoverForSubgraphType(String sgType, String tenantId) {
        List<MindMapNode> nodes = store.search(
            MindMapQuery.of(tenantId, 10000).withType(sgType));
        if (nodes.size() < minSamples) return;

        Map<String, String> nodeTypes = new HashMap<>();
        for (MindMapNode node : nodes) {
            String type = node.property("cognitiveKind").orElse(sgType);
            nodeTypes.put(node.id(), type);
        }

        Map<String, List<MindMapNode>> byType = new HashMap<>();
        for (MindMapNode node : nodes) {
            byType.computeIfAbsent(nodeTypes.get(node.id()), k -> new ArrayList<>()).add(node);
        }

        for (var entry : byType.entrySet()) {
            String typeName = entry.getKey();
            List<MindMapNode> typeNodes = entry.getValue();
            if (typeNodes.size() < minSamples) continue;
            analyzeType(typeName, typeNodes, tenantId);
        }
    }

    private void analyzeType(String typeName, List<MindMapNode> nodes, String tenantId) {
        int totalNodes = nodes.size();

        Map<String, List<String>> propertyValues = new HashMap<>();
        for (MindMapNode node : nodes) {
            for (var prop : node.properties().entrySet()) {
                if (prop.getKey().startsWith(SCHEMA_PREFIX)) continue;
                if (SKIP_PROPERTIES.contains(prop.getKey())) continue;
                propertyValues.computeIfAbsent(prop.getKey(), k -> new ArrayList<>())
                    .add(prop.getValue());
            }
        }

        MindMapNode typeNode = findTypeNode(typeName, tenantId);
        if (typeNode == null) return;

        Map<String, String> updates = new LinkedHashMap<>();
        String epochStr = String.valueOf(Instant.now().getEpochSecond());

        for (var prop : propertyValues.entrySet()) {
            String fieldName = prop.getKey();
            List<String> values = prop.getValue();
            double frequency = (double) values.size() / totalNodes;
            if (frequency < threshold) continue;

            if (isJavaSourced(typeNode, fieldName)) continue;

            String inferredType = inferType(values);
            boolean isCollection = inferCollection(values);
            List<String> enumVals = inferEnumValues(values);
            boolean required = frequency >= requiredThreshold;

            updates.put(SCHEMA_PREFIX + fieldName + ".type", inferredType);
            updates.put(SCHEMA_PREFIX + fieldName + ".required", String.valueOf(required));
            updates.put(SCHEMA_PREFIX + fieldName + ".source", "discovered");
            if (isCollection) {
                updates.put(SCHEMA_PREFIX + fieldName + ".collection", "true");
            }
            if (enumVals != null) {
                updates.put(SCHEMA_PREFIX + fieldName + ".enum", String.join(",", enumVals));
            }
            if (typeNode.property(SCHEMA_PREFIX + fieldName + ".first-seen-epoch").isEmpty()) {
                updates.put(SCHEMA_PREFIX + fieldName + ".first-seen-epoch", epochStr);
            }
        }

        if (!updates.isEmpty()) {
            store.updateNode(typeNode.id(), new NodeUpdate(
                null, null, null, null, null, null, null, null,
                null, null, null, updates, null), tenantId);
            LOG.info("Discovered schema field(s) for type '" + typeName
                + "' in tenant '" + tenantId + "'");
        }
    }

    private boolean isJavaSourced(MindMapNode typeNode, String fieldName) {
        return typeNode.property(SCHEMA_PREFIX + fieldName + ".source")
            .map("java"::equals).orElse(false);
    }

    private MindMapNode findTypeNode(String typeName, String tenantId) {
        return store.search(MindMapQuery.of(tenantId, 100)
            .withType(SubgraphTypes.TYPE_SYSTEM)).stream()
            .filter(n -> n.name().equals(typeName))
            .findFirst().orElse(null);
    }

    static String inferType(List<String> values) {
        boolean allNumeric = values.stream().allMatch(v -> {
            try { Double.parseDouble(v); return true; }
            catch (NumberFormatException e) { return false; }
        });
        if (allNumeric) return "number";

        boolean allBoolean = values.stream().allMatch(v ->
            "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v));
        if (allBoolean) return "boolean";

        return "string";
    }

    static boolean inferCollection(List<String> values) {
        long commaCount = values.stream()
            .filter(v -> v.contains(",") && v.split(",").length >= 2)
            .count();
        return (double) commaCount / values.size() >= 0.6;
    }

    static List<String> inferEnumValues(List<String> values) {
        Set<String> distinct = new LinkedHashSet<>(values);
        if (distinct.size() <= MAX_ENUM_VALUES && distinct.size() < values.size()) {
            return List.copyOf(distinct);
        }
        return null;
    }
}
