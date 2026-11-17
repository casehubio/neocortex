package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SituationalStateService {

    private final MindMapStore store;
    private final Set<String> facetNames;
    private final ConcurrentHashMap<String, String> subgraphIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PersonalityState> stateCache = new ConcurrentHashMap<>();

    public SituationalStateService(MindMapStore store, Set<String> facetNames) {
        this.store = store;
        this.facetNames = Set.copyOf(facetNames);
    }

    public PersonalityState loadOrCreate(String tenantId, String agentId, Instant now) {
        var key = slotKey(agentId, tenantId);
        var cached = stateCache.get(key);
        if (cached != null) return cached;

        var sgId = resolveSubgraph(tenantId);
        var nodeName = nodeName(agentId);
        var node = store.resolveNode(nodeName, sgId, tenantId);
        if (node != null) {
            var state = fromProperties(node.properties(), now);
            stateCache.put(key, state);
            return state;
        }

        var state = PersonalityState.initial(facetNames, now);
        var props = toProperties(state);
        store.addNode(NodeInput.of(nodeName, sgId)
                .withProperties(props)
                .withTraits(Set.of("situational-personality")), tenantId);
        stateCache.put(key, state);
        return state;
    }

    public void persist(String tenantId, String agentId, PersonalityState state) {
        var key = slotKey(agentId, tenantId);
        var sgId = resolveSubgraph(tenantId);
        var nodeName = nodeName(agentId);
        var node = store.resolveNode(nodeName, sgId, tenantId);
        if (node != null) {
            store.updateNode(node.id(),
                    NodeUpdate.empty().withPropertiesToSet(toProperties(state)), tenantId);
        } else {
            store.addNode(NodeInput.of(nodeName, sgId)
                    .withProperties(toProperties(state))
                    .withTraits(Set.of("situational-personality")), tenantId);
        }
        stateCache.put(key, state);
    }

    public PersonalityState currentState(String tenantId, String agentId) {
        return stateCache.get(slotKey(agentId, tenantId));
    }

    public static io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot toSnapshot(PersonalityState state) {
        var facets = new java.util.LinkedHashMap<String, io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot.FacetDisplacement>();
        for (var entry : state.facets().entrySet()) {
            var fs = entry.getValue();
            facets.put(entry.getKey(),
                    new io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot.FacetDisplacement(fs.homeOffset(), fs.displacement()));
        }
        return new io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot(facets, System.nanoTime());
    }

    public java.util.Set<String> cachedAgentIds(String tenantId) {
        var suffix = ":" + tenantId;
        var ids    = new java.util.LinkedHashSet<String>();
        for (var key : stateCache.keySet()) {
            if (key.endsWith(suffix)) {
                ids.add(key.substring(0, key.length() - suffix.length()));
            }
        }
        return Set.copyOf(ids);
    }


    private String resolveSubgraph(String tenantId) {
        return subgraphIds.computeIfAbsent(tenantId, t -> {
            var existing = store.listSubgraphs(t).stream()
                    .filter(sg -> SubgraphTypes.PERSONALITY_STATE.equals(sg.type()))
                    .findFirst();
            if (existing.isPresent()) return existing.get().id();
            return store.createSubgraph(
                    new SubgraphInput("Personality State", SubgraphTypes.PERSONALITY_STATE, null), t);
        });
    }

    static String nodeName(String agentId) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(agentId.getBytes(StandardCharsets.UTF_8));
            var encoded = Base64.getEncoder().withoutPadding().encodeToString(hash)
                    .replace('+', '-').replace('/', '_')
                    .substring(0, 16);
            return "sit-state:" + encoded;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static Map<String, String> toProperties(PersonalityState state) {
        var props = new LinkedHashMap<String, String>();
        for (var entry : state.facets().entrySet()) {
            var prefix = "facet." + entry.getKey() + ".";
            var fs = entry.getValue();
            props.put(prefix + "displacement", String.valueOf(fs.displacement()));
            props.put(prefix + "homeOffset", String.valueOf(fs.homeOffset()));
            props.put(prefix + "resilience", String.valueOf(fs.resilience()));
            props.put(prefix + "exposure", String.valueOf(fs.exposure()));
            props.put(prefix + "load", String.valueOf(fs.load()));
        }
        for (var mode : SituationalMode.values()) {
            props.put("intensity." + mode.name(), String.valueOf(state.intensity(mode)));
            props.put("engaged." + mode.name(), String.valueOf(state.isEngaged(mode)));
        }
        for (var entry : state.winners().entrySet()) {
            props.put("winner." + entry.getKey().name(), entry.getValue().name());
        }
        props.put("cursorTime", state.cursorTime().toString());
        props.put("hourCursor", state.hourCursor().toString());
        props.put("exposedUntil", state.exposedUntil().toString());
        return Map.copyOf(props);
    }

    PersonalityState fromProperties(Map<String, String> props, Instant fallbackNow) {
        var facets = new LinkedHashMap<String, FacetState>();
        for (String facetName : facetNames) {
            var prefix = "facet." + facetName + ".";
            if (!props.containsKey(prefix + "displacement")) {
                facets.put(facetName, FacetState.initial());
                continue;
            }
            facets.put(facetName, new FacetState(
                    Double.parseDouble(props.get(prefix + "displacement")),
                    Double.parseDouble(props.get(prefix + "homeOffset")),
                    Double.parseDouble(props.get(prefix + "resilience")),
                    Double.parseDouble(props.get(prefix + "exposure")),
                    Double.parseDouble(props.get(prefix + "load"))));
        }
        var intensities = new EnumMap<SituationalMode, Double>(SituationalMode.class);
        var engaged = new EnumMap<SituationalMode, Boolean>(SituationalMode.class);
        for (var mode : SituationalMode.values()) {
            intensities.put(mode, parseDouble(props.get("intensity." + mode.name()), 0.0));
            engaged.put(mode, Boolean.parseBoolean(props.get("engaged." + mode.name())));
        }
        var winners = new EnumMap<SituationalMode.Group, SituationalMode>(SituationalMode.Group.class);
        for (var group : SituationalMode.Group.values()) {
            var winnerStr = props.get("winner." + group.name());
            if (winnerStr != null) {
                try { winners.put(group, SituationalMode.valueOf(winnerStr)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        var cursorTime = parseInstant(props.get("cursorTime"), fallbackNow);
        var hourCursor = parseInstant(props.get("hourCursor"), PersonalityState.truncateToHour(fallbackNow));
        var exposedUntil = parseInstant(props.get("exposedUntil"), Instant.EPOCH);
        return new PersonalityState(facets, intensities, engaged, winners, cursorTime, hourCursor, exposedUntil);
    }

    private static double parseDouble(String v, double fallback) {
        if (v == null) return fallback;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static Instant parseInstant(String v, Instant fallback) {
        if (v == null) return fallback;
        try { return Instant.parse(v); }
        catch (Exception e) { return fallback; }
    }

    private static String slotKey(String agentId, String tenantId) {
        return agentId + ":" + tenantId;
    }
}
