package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapConfidenceDefaults;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SchemaField;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.api.identity.PrincipalId;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Singleton
public class MindMapExtractor {

    private static final Logger LOG = Logger.getLogger(MindMapExtractor.class.getName());
    private static final int MAX_CONTEXT_NODES = 20;

    private static final String SYSTEM_PROMPT = """
        You are a knowledge graph extraction agent. Given a conversation turn \
        and existing graph context, extract entities and relationships.

        Respond with a JSON object:
        {
          "entities": [
            {"name": "...", "type": "PERSON|PROJECT|RESEARCH_AREA|ORGANISATION|CONCEPT|GENERAL|BELIEF|INTENTION|PREDICTION|JUDGMENT|FEAR|DESIRE", \
        "properties": {"key": "value"}, "confidence": "STATED|INFERRED|SPECULATED"}
          ],
          "relationships": [
            {"source": "entity name", "target": "entity name", "type": "relationship-type", \
        "confidence": "STATED|INFERRED|SPECULATED"}
          ],
          "contradictions": [
            {"entity": "name", "property": "edge-or-property", "existing": "old value", \
        "extracted": "new value", "explanation": "why they conflict"}
          ]
        }

        Rules:
        - Extract only entities and relationships explicitly or strongly implied in the conversation
        - Use the existing graph context to avoid creating duplicates
        - Use recently mentioned entities to resolve pronouns
        - Flag contradictions when extracted facts conflict with existing graph
        - For cognitive content (beliefs, intentions, predictions, judgments, fears, desires), \
        classify by the PRIMARY cognitive aspect
        - Respond with valid JSON only""";

    private static final Set<String> COMMON_WORDS = Set.of(
        "The", "A", "An", "I", "We", "He", "She", "It", "They",
        "This", "That", "These", "Those", "My", "Your", "His", "Her",
        "Its", "Our", "Their", "And", "But", "Or", "So", "If", "When",
        "Where", "How", "What", "Who", "Which", "Not", "No", "Yes",
        "Also", "Just", "Now", "Then", "Here", "There");
    private static final Set<String> COGNITIVE_TYPES = Set.of(
            "belief", "intention", "prediction", "judgment", "fear", "desire");

    static String resolveSubgraphType(String normalizedType) {
        if (COGNITIVE_TYPES.contains(normalizedType)) {return SubgraphTypes.COGNITIVE;}
        return normalizedType;
    }


    private final MindMapStore store;
    private final Instance<AgentProvider> agentProviderInstance;
    private final TypeRegistry typeRegistry;
    private final Map<String, Map<String, String>> subgraphCache = new ConcurrentHashMap<>();

    @Inject
    public MindMapExtractor(MindMapStore store, Instance<AgentProvider> agentProviderInstance,
                             Instance<TypeRegistry> typeRegistryInstance) {
        this.store = store;
        this.agentProviderInstance = agentProviderInstance;
        this.typeRegistry = typeRegistryInstance.isResolvable() ? typeRegistryInstance.get() : null;
    }

    MindMapExtractor(MindMapStore store, Instance<AgentProvider> agentProviderInstance) {
        this(store, agentProviderInstance, (TypeRegistry) null);
    }

    MindMapExtractor(MindMapStore store, Instance<AgentProvider> agentProviderInstance,
                      TypeRegistry typeRegistry) {
        this.store = store;
        this.agentProviderInstance = agentProviderInstance;
        this.typeRegistry = typeRegistry;
    }


    public ParsedExtraction parse(String conversationText, String tenantId,
                                  List<String> recentEntityNames) {
        if (conversationText == null || conversationText.isBlank()) {return null;}
        if (agentProviderInstance.isUnsatisfied()) {return null;}

        Map<String, List<MindMapEdge>> context =
                retrieveContext(conversationText, tenantId, recentEntityNames);
        String userPrompt  = buildUserPrompt(conversationText, context, recentEntityNames, tenantId);
        String llmResponse = invokeLlm(SYSTEM_PROMPT, userPrompt);
        if (llmResponse == null) {return null;}

        return ExtractionJsonParser.parse(llmResponse);
    }

    public ExtractionResult apply(ParsedExtraction parsed, String tenantId) {
        return apply(parsed, tenantId, null);
    }

    public ExtractionResult apply(ParsedExtraction parsed, String tenantId,
                                  PrincipalId principalId) {
        return applyExtraction(parsed, tenantId, principalId);
    }


    public ExtractionResult extract(String conversationText, String tenantId) {
        return extract(conversationText, tenantId, List.of());
    }

    public ExtractionResult extract(String conversationText, String tenantId,
                                     List<String> recentEntityNames) {
        ParsedExtraction parsed = parse(conversationText, tenantId, recentEntityNames);
        if (parsed == null) {return ExtractionResult.EMPTY;}
        return apply(parsed, tenantId);
    }

    private Map<String, List<MindMapEdge>> retrieveContext(String conversationText,
                                                            String tenantId,
                                                            List<String> recentEntityNames) {
        Map<String, List<MindMapEdge>> context = new LinkedHashMap<>();

        for (String name : recentEntityNames) {
            MindMapNode node = store.resolveNode(name, null, tenantId);
            if (node != null) {
                context.put(node.id(), store.neighbors(node.id(), tenantId));
            }
        }

        for (String term : extractCandidateTerms(conversationText)) {
            if (context.size() >= MAX_CONTEXT_NODES) break;
            List<MindMapNode> hits = store.search(
                MindMapQuery.of(tenantId, 5).withText(term));
            for (MindMapNode node : hits) {
                context.computeIfAbsent(node.id(), id -> store.neighbors(id, tenantId));
            }
        }
        return context;
    }

    List<String> extractCandidateTerms(String text) {
        List<String> terms = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String clean = word.replaceAll("[^a-zA-Z0-9'-]", "");
            if (clean.isEmpty()) continue;
            if (Character.isUpperCase(clean.charAt(0)) && !COMMON_WORDS.contains(clean)) {
                if (!current.isEmpty()) current.append(' ');
                current.append(clean);
            } else {
                if (!current.isEmpty()) {
                    terms.add(current.toString());
                    current.setLength(0);
                }
            }
        }
        if (!current.isEmpty()) terms.add(current.toString());
        return terms;
    }

    private String buildUserPrompt(String conversationText,
                                    Map<String, List<MindMapEdge>> context,
                                    List<String> recentEntityNames,
                                    String tenantId) {
        var sb = new StringBuilder();
        sb.append("Conversation:\n").append(conversationText).append("\n\n");

        if (!context.isEmpty()) {
            sb.append("Existing graph context:\n");
            for (var entry : context.entrySet()) {
                MindMapNode node = store.getNode(entry.getKey(), tenantId);
                if (node == null) continue;
                sb.append("- ").append(node.name());
                sb.append(" (confidence: ").append(String.format("%.2f", node.confidence().value()));
                if (!node.traits().isEmpty()) {
                    sb.append(", traits: ").append(String.join(", ", node.traits()));
                }
                sb.append(")\n");
                for (MindMapEdge edge : entry.getValue()) {
                    sb.append("  ");
                    if (edge.sourceNodeId().equals(node.id())) {
                        sb.append("→ ").append(edge.edgeType()).append(" → ");
                        MindMapNode target = store.getNode(edge.targetNodeId(), tenantId);
                        sb.append(target != null ? target.name() : edge.targetNodeId());
                    } else {
                        sb.append("← ").append(edge.edgeType()).append(" ← ");
                        MindMapNode source = store.getNode(edge.sourceNodeId(), tenantId);
                        sb.append(source != null ? source.name() : edge.sourceNodeId());
                    }
                    sb.append(" [").append(edge.confidence().origin()).append("]\n");
                }
            }
            sb.append('\n');
        }

        if (!recentEntityNames.isEmpty()) {
            sb.append("Recently mentioned entities:\n");
            sb.append(String.join(", ", recentEntityNames)).append("\n");
        }

        if (typeRegistry != null) {
            Map<String, Map<String, SchemaField>> schemas = new LinkedHashMap<>();
            Set<String> contextTypes = new HashSet<>();
            for (var entry : context.entrySet()) {
                MindMapNode node = store.getNode(entry.getKey(), tenantId);
                if (node != null && node.subgraphType() != null) {
                    String type = node.property("cognitiveKind").orElse(node.subgraphType());
                    contextTypes.add(type);
                }
            }
            for (String type : contextTypes) {
                Map<String, SchemaField> schema = typeRegistry.schemaFor(type, tenantId);
                if (!schema.isEmpty()) {
                    schemas.put(type, schema);
                }
            }
            String schemaHint = buildSchemaHint(schemas);
            if (!schemaHint.isEmpty()) {
                sb.append(schemaHint);
            }
        }

        return sb.toString();
    }

    static String buildSchemaHint(Map<String, Map<String, SchemaField>> schemas) {
        if (schemas.isEmpty()) return "";
        var sb = new StringBuilder();
        sb.append("Known type schemas:\n");
        for (var entry : schemas.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": {");
            var fields = new ArrayList<String>();
            for (var field : entry.getValue().values()) {
                var desc = new StringBuilder(field.name()).append(": ").append(field.type());
                var qualifiers = new ArrayList<String>();
                if (field.required()) qualifiers.add("required");
                if (field.collection()) qualifiers.add("collection");
                if (!qualifiers.isEmpty()) {
                    desc.append(" (").append(String.join(", ", qualifiers)).append(")");
                }
                if (field.enumValues() != null && !field.enumValues().isEmpty()) {
                    desc.append(" [").append(String.join(", ", field.enumValues())).append("]");
                }
                if (field.description() != null) {
                    desc.append(" — ").append(field.description());
                }
                fields.add(desc.toString());
            }
            sb.append(String.join(", ", fields));
            sb.append("}\n");
        }
        sb.append("\nExtract all observed properties, including those not listed in known schemas.\n");
        return sb.toString();
    }

    private ExtractionResult applyExtraction(ParsedExtraction parsed, String tenantId,
                                             PrincipalId principalId) {
        List<ExtractedEntity>       entities      = new ArrayList<>();
        List<ExtractedRelationship> relationships = new ArrayList<>();
        List<String>                entityNames   = new ArrayList<>();
        Map<String, String>         nameToNodeId  = new HashMap<>();

        List<NodeInput> newNodeInputs    = new ArrayList<>();
        List<Integer>   newEntityIndices = new ArrayList<>();

        for (int i = 0; i < parsed.entities().size(); i++) {
            ParsedEntity pe             = parsed.entities().get(i);
            String       normalizedType = normalizeType(pe.type());
            String       sgType         = resolveSubgraphType(normalizedType);
            String       sgId           = findOrCreateSubgraph(sgType, tenantId);

            Map<String, String> nodeProps = pe.properties() != null
                                            ? new HashMap<>(pe.properties()) : new HashMap<>();
            if (COGNITIVE_TYPES.contains(normalizedType)) {
                nodeProps.put("cognitiveKind", normalizedType);
            }

            MindMapNode existing = store.resolveNode(pe.name(), null, tenantId);

            if (existing != null) {
                nameToNodeId.put(pe.name(), existing.id());
                entityNames.add(pe.name());
                entities.add(new ExtractedEntity(existing.id(), pe.name(), false, sgType, nodeProps));
                if (!nodeProps.isEmpty()) {
                    store.updateNode(existing.id(),
                                     new NodeUpdate(null, null,
                                                    null, null, null, null, null, null,
                                                    null, null, null,
                                                    nodeProps, null), tenantId);
                }
            } else {
                newNodeInputs.add(new NodeInput(
                        pe.name(), sgId,
                        MindMapConfidenceDefaults.forOrigin(pe.origin(), Instant.now()),
                        "llm-extraction", null, null, null, null,
                        null, null, null,
                        nodeProps, principalId, null));
                newEntityIndices.add(i);
            }
        }

        List<String> newNodeIds = store.addNodes(newNodeInputs, tenantId);
        for (int j = 0; j < newNodeIds.size(); j++) {
            int          entityIdx      = newEntityIndices.get(j);
            ParsedEntity pe             = parsed.entities().get(entityIdx);
            String       normalizedType = normalizeType(pe.type());
            String       sgType         = resolveSubgraphType(normalizedType);
            Map<String, String> nodeProps = pe.properties() != null
                                            ? new HashMap<>(pe.properties()) : new HashMap<>();
            if (COGNITIVE_TYPES.contains(normalizedType)) {
                nodeProps.put("cognitiveKind", normalizedType);
            }
            nameToNodeId.put(pe.name(), newNodeIds.get(j));
            entityNames.add(pe.name());
            entities.add(new ExtractedEntity(newNodeIds.get(j), pe.name(), true, sgType, nodeProps));
        }

        List<EdgeInput>          edgeInputs         = new ArrayList<>();
        List<ParsedRelationship> validRelationships = new ArrayList<>();
        for (ParsedRelationship pr : parsed.relationships()) {
            String sourceId = resolveNodeId(pr.source(), nameToNodeId, tenantId);
            String targetId = resolveNodeId(pr.target(), nameToNodeId, tenantId);
            if (sourceId != null && targetId != null) {
                edgeInputs.add(new EdgeInput(
                        sourceId, targetId, pr.type(),
                        MindMapConfidenceDefaults.forOrigin(pr.origin(), Instant.now()),
                        "llm-extraction", null, null, null, null, null, Map.of(),
                        principalId));
                validRelationships.add(pr);
            }
        }
        List<String> edgeIds = store.addEdges(edgeInputs, tenantId);
        for (int k = 0; k < edgeIds.size(); k++) {
            ParsedRelationship pr = validRelationships.get(k);
            relationships.add(new ExtractedRelationship(
                    edgeIds.get(k), pr.source(), pr.target(), pr.type(), pr.origin()));
        }

        List<Contradiction> contradictions = new ArrayList<>();
        for (ParsedContradiction pc : parsed.contradictions()) {
            contradictions.add(new Contradiction(
                    pc.entity(), pc.property(), pc.existing(), pc.extracted(), pc.explanation()));
        }

        return new ExtractionResult(entities, relationships, contradictions, entityNames);
    }

    private String resolveNodeId(String name, Map<String, String> nameToNodeId, String tenantId) {
        String id = nameToNodeId.get(name);
        if (id != null) return id;
        MindMapNode node = store.resolveNode(name, null, tenantId);
        return node != null ? node.id() : null;
    }

    private String findOrCreateSubgraph(String type, String tenantId) {
        Map<String, String> tenantCache = subgraphCache.computeIfAbsent(tenantId, t -> {
            Map<String, String> warm = new ConcurrentHashMap<>();
            for (MindMapSubgraph sg : store.listSubgraphs(t)) {
                warm.putIfAbsent(sg.type(), sg.id());
            }
            return warm;
        });
        return tenantCache.computeIfAbsent(type, t ->
            store.createSubgraph(new SubgraphInput(t, t, null), tenantId));
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) return SubgraphTypes.GENERAL;
        return type.strip().toLowerCase();
    }

    private String invokeLlm(String systemPrompt, String userPrompt) {
        AgentProvider provider = agentProviderInstance.get();
        try {
            var events = provider.invoke(AgentSessionConfig.of(systemPrompt, userPrompt))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2));

            boolean hasError = events.stream()
                .filter(AgentEvent.InvocationComplete.class::isInstance)
                .map(AgentEvent.InvocationComplete.class::cast)
                .anyMatch(AgentEvent.InvocationComplete::isError);
            if (hasError) {
                LOG.warning("LLM invocation completed with error flag");
                return null;
            }

            String text = events.stream()
                .filter(AgentEvent.TextDelta.class::isInstance)
                .map(AgentEvent.TextDelta.class::cast)
                .map(AgentEvent.TextDelta::text)
                .collect(Collectors.joining());

            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            LOG.warning("LLM invocation failed: " + e.getMessage());
            return null;
        }
    }
}
