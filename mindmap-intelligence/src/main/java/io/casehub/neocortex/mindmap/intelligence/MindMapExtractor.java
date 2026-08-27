package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphType;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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
            {"name": "...", "type": "PERSON|PROJECT|RESEARCH_AREA|ORGANISATION|CONCEPT|GENERAL", \
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
        - Respond with valid JSON only""";

    private static final Set<String> COMMON_WORDS = Set.of(
        "The", "A", "An", "I", "We", "He", "She", "It", "They",
        "This", "That", "These", "Those", "My", "Your", "His", "Her",
        "Its", "Our", "Their", "And", "But", "Or", "So", "If", "When",
        "Where", "How", "What", "Who", "Which", "Not", "No", "Yes",
        "Also", "Just", "Now", "Then", "Here", "There");

    private final MindMapStore store;
    private final Instance<AgentProvider> agentProviderInstance;
    private final Map<String, Map<SubgraphType, String>> subgraphCache = new ConcurrentHashMap<>();

    @Inject
    public MindMapExtractor(MindMapStore store, Instance<AgentProvider> agentProviderInstance) {
        this.store = store;
        this.agentProviderInstance = agentProviderInstance;
    }

    public ExtractionResult extract(String conversationText, String tenantId) {
        return extract(conversationText, tenantId, List.of());
    }

    public ExtractionResult extract(String conversationText, String tenantId,
                                     List<String> recentEntityNames) {
        if (conversationText == null || conversationText.isBlank()) return ExtractionResult.EMPTY;
        if (agentProviderInstance.isUnsatisfied()) return ExtractionResult.EMPTY;

        Map<String, List<MindMapEdge>> context =
            retrieveContext(conversationText, tenantId, recentEntityNames);
        String userPrompt = buildUserPrompt(conversationText, context, recentEntityNames, tenantId);
        String llmResponse = invokeLlm(SYSTEM_PROMPT, userPrompt);
        if (llmResponse == null) return ExtractionResult.EMPTY;

        ParsedExtraction parsed = ExtractionJsonParser.parse(llmResponse);
        if (parsed == null) return ExtractionResult.EMPTY;

        return applyExtraction(parsed, tenantId);
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
                new MindMapQuery(tenantId, null, term, null, null, null, null, false, 5));
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
                sb.append(" (confidence: ").append(String.format("%.2f", node.confidence()));
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
                    sb.append(" [").append(edge.confidenceOrigin()).append("]\n");
                }
            }
            sb.append('\n');
        }

        if (!recentEntityNames.isEmpty()) {
            sb.append("Recently mentioned entities:\n");
            sb.append(String.join(", ", recentEntityNames)).append("\n");
        }
        return sb.toString();
    }

    private ExtractionResult applyExtraction(ParsedExtraction parsed, String tenantId) {
        List<ExtractedEntity> entities = new ArrayList<>();
        List<ExtractedRelationship> relationships = new ArrayList<>();
        List<String> entityNames = new ArrayList<>();
        Map<String, String> nameToNodeId = new HashMap<>();

        for (ParsedEntity pe : parsed.entities()) {
            SubgraphType sgType = parseSubgraphType(pe.type());
            String sgId = findOrCreateSubgraph(sgType, tenantId);

            MindMapNode existing = store.resolveNode(pe.name(), null, tenantId);
            String nodeId;
            boolean created;

            if (existing != null) {
                nodeId = existing.id();
                created = false;
                if (pe.properties() != null && !pe.properties().isEmpty()) {
                    store.updateNode(nodeId,
                        new NodeUpdate(null, null, null, null,
                            null, null, null, null, null, null,
                            null, null, null,
                            pe.properties(), null), tenantId);
                }
            } else {
                nodeId = store.addNode(new NodeInput(
                    pe.name(), sgId, pe.confidence(), null,
                    "llm-extraction", null, null, null, null,
                    null, null, null,
                    pe.properties() != null ? pe.properties() : Map.of()), tenantId);
                created = true;
            }

            nameToNodeId.put(pe.name(), nodeId);
            entityNames.add(pe.name());
            entities.add(new ExtractedEntity(nodeId, pe.name(), created,
                sgType.name(),
                pe.properties() != null ? pe.properties() : Map.of()));
        }

        for (ParsedRelationship pr : parsed.relationships()) {
            String sourceId = resolveNodeId(pr.source(), nameToNodeId, tenantId);
            String targetId = resolveNodeId(pr.target(), nameToNodeId, tenantId);
            if (sourceId != null && targetId != null) {
                String edgeId = store.addEdge(new EdgeInput(
                    sourceId, targetId, pr.type(), pr.confidence(), null,
                    "llm-extraction", null, null, null, null, null, Map.of()), tenantId);
                relationships.add(new ExtractedRelationship(
                    edgeId, pr.source(), pr.target(), pr.type(), pr.confidence()));
            }
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

    private String findOrCreateSubgraph(SubgraphType type, String tenantId) {
        Map<SubgraphType, String> tenantCache = subgraphCache.computeIfAbsent(tenantId, t -> {
            Map<SubgraphType, String> warm = new ConcurrentHashMap<>();
            for (MindMapSubgraph sg : store.listSubgraphs(t)) {
                warm.putIfAbsent(sg.type(), sg.id());
            }
            return warm;
        });
        return tenantCache.computeIfAbsent(type, t ->
            store.createSubgraph(new SubgraphInput(t.name(), t, null), tenantId));
    }

    private SubgraphType parseSubgraphType(String type) {
        if (type == null) return SubgraphType.GENERAL;
        try {
            return SubgraphType.valueOf(type);
        } catch (IllegalArgumentException e) {
            LOG.fine("Unknown entity type '" + type + "', mapping to GENERAL");
            return SubgraphType.GENERAL;
        }
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
