package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot;
import io.casehub.neocortex.cognitive.index.PersonalityStateSource;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

public class PersonalityStateService implements PersonalityStateSource {

    private static final int STRIPE_COUNT = 256;
    static final double EPSILON_PUBLISH = 0.005;

    private static final Logger LOG = Logger.getLogger(PersonalityStateService.class.getName());

    private final MindMapStore store;
    private final Set<String> facetNames;
    private final ConcurrentHashMap<String, String> subgraphIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TenantAgentKey, PersonalityState> stateCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TenantAgentKey, Long> compositionVersionCache = new ConcurrentHashMap<>();
    private final ReentrantLock[] stripes;

    public PersonalityStateService(MindMapStore store, Set<String> facetNames) {
        this.store = store;
        this.facetNames = Set.copyOf(facetNames);
        this.stripes = new ReentrantLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    public PersonalityState transition(String tenantId, String agentId, Instant now, UnaryOperator<PersonalityState> fn) {
        var key = new TenantAgentKey(tenantId, agentId);
        var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
        lock.lock();
        try {
            var current = loadOrCreateUnderLock(key, now);
            var next = fn.apply(current);
            if (next == null) throw new IllegalStateException("transition fn must not return null");
            var nextVersion = current.version() + 1;
            var compVersionBefore = computeCompositionKey(current);
            var compVersionAfter = computeCompositionKey(next);
            var newCompVersion = compositionVersionCache.getOrDefault(key, 0L);
            if (!compVersionBefore.equals(compVersionAfter)) {
                newCompVersion = newCompVersion + 1;
            }
            var withVersion = next.withVersion(nextVersion);
            persistUnderLock(key, withVersion);
            stateCache.put(key, withVersion);
            compositionVersionCache.put(key, newCompVersion);
            return withVersion;
        } finally {
            lock.unlock();
        }
    }

    public PersonalityState read(String tenantId, String agentId, Instant now) {
        var key = new TenantAgentKey(tenantId, agentId);
        var cached = stateCache.get(key);
        if (cached != null) return cached;
        var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
        lock.lock();
        try {
            var second = stateCache.get(key);
            if (second != null) return second;
            var state = loadOrCreateUnderLock(key, now);
            stateCache.put(key, state);
            return state;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<PersonalityStateSnapshot> find(String tenantId, String agentId, Instant now) {
        var key = new TenantAgentKey(tenantId, agentId);
        var cached = stateCache.get(key);
        if (cached != null) return Optional.of(toSnapshot(cached));
        var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
        lock.lock();
        try {
            var second = stateCache.get(key);
            if (second != null) return Optional.of(toSnapshot(second));
            var sgId = resolveSubgraph(tenantId);
            var node = store.resolveNode(nodeName(agentId), sgId, tenantId);
            if (node == null) return Optional.empty();
            var state = fromProperties(node.properties(), now);
            stateCache.put(key, state);
            return Optional.of(toSnapshot(state));
        } finally {
            lock.unlock();
        }
    }

    public long compositionVersion(String tenantId, String agentId) {
        var key = new TenantAgentKey(tenantId, agentId);
        var cached = compositionVersionCache.get(key);
        if (cached != null) return cached;
        var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
        lock.lock();
        try {
            var second = compositionVersionCache.get(key);
            if (second != null) return second;
            var sgId = resolveSubgraph(tenantId);
            var node = store.resolveNode(nodeName(agentId), sgId, tenantId);
            if (node == null) return 0;
            return 0;
        } finally {
            lock.unlock();
        }
    }

    public Set<String> agentIds(String tenantId) {
        var sgId = resolveSubgraph(tenantId);
        var query = io.casehub.neocortex.mindmap.MindMapQuery.of(tenantId, 2000)
                .withSubgraphId(sgId)
                .withTraits(Set.of("situational-personality"));
        var nodes = store.search(query);
        var ids = new LinkedHashSet<String>();
        for (var node : nodes) {
            var aid = node.properties().get("agentId");
            if (aid != null) ids.add(aid);
        }
        for (var entry : stateCache.entrySet()) {
            if (tenantId.equals(entry.getKey().tenantId())) {
                ids.add(entry.getKey().agentId());
            }
        }
        return Set.copyOf(ids);
    }

    PersonalityState loadOrCreate(String tenantId, String agentId, Instant now) {
        var key = new TenantAgentKey(tenantId, agentId);
        var cached = stateCache.get(key);
        if (cached != null) return cached;
        return read(tenantId, agentId, now);
    }

    void persist(String tenantId, String agentId, PersonalityState state) {
        var key = new TenantAgentKey(tenantId, agentId);
        persistUnderLock(key, state);
        stateCache.put(key, state);
    }

    public PersonalityState currentState(String tenantId, String agentId) {
        return stateCache.get(new TenantAgentKey(tenantId, agentId));
    }

    public static PersonalityStateSnapshot toSnapshot(PersonalityState state) {
        var facets = new LinkedHashMap<String, PersonalityStateSnapshot.FacetDisplacement>();
        for (var entry : state.facets().entrySet()) {
            var fs = entry.getValue();
            facets.put(entry.getKey(),
                    new PersonalityStateSnapshot.FacetDisplacement(fs.homeOffset(), fs.displacement(), fs.promoted()));
        }
        return new PersonalityStateSnapshot(facets, state.version(), 0);
    }

    public Set<String> cachedAgentIds(String tenantId) {
        var ids = new LinkedHashSet<String>();
        for (var entry : stateCache.entrySet()) {
            if (tenantId.equals(entry.getKey().tenantId())) {
                ids.add(entry.getKey().agentId());
            }
        }
        return Set.copyOf(ids);
    }

    private PersonalityState loadOrCreateUnderLock(TenantAgentKey key, Instant now) {
        var cached = stateCache.get(key);
        if (cached != null) return cached;
        var sgId = resolveSubgraph(key.tenantId());
        var nodeName = nodeName(key.agentId());
        var node = store.resolveNode(nodeName, sgId, key.tenantId());
        if (node != null) {
            var state = fromProperties(node.properties(), now);
            stateCache.put(key, state);
            return state;
        }
        var state = PersonalityState.initial(facetNames, now);
        var props = toProperties(state);
        var input = NodeInput.of(nodeName, sgId)
                .withProperties(props)
                .withTraits(Set.of("situational-personality"));
        var propsWithAgent = new LinkedHashMap<>(props);
        propsWithAgent.put("agentId", key.agentId());
        input = input.withProperties(Map.copyOf(propsWithAgent));
        store.addNode(input, key.tenantId());
        stateCache.put(key, state);
        return state;
    }

    private void persistUnderLock(TenantAgentKey key, PersonalityState state) {
        var sgId = resolveSubgraph(key.tenantId());
        var nodeName = nodeName(key.agentId());
        var node = store.resolveNode(nodeName, sgId, key.tenantId());
        var props = new LinkedHashMap<>(toProperties(state));
        props.put("agentId", key.agentId());
        if (node != null) {
            store.updateNode(node.id(),
                    NodeUpdate.empty().withPropertiesToSet(Map.copyOf(props)), key.tenantId());
        } else {
            store.addNode(NodeInput.of(nodeName, sgId)
                    .withProperties(Map.copyOf(props))
                    .withTraits(Set.of("situational-personality")), key.tenantId());
        }
    }

    String computeCompositionKey(PersonalityState state) {
        var sb = new StringBuilder();
        state.facets().entrySet().stream()
             .sorted(Map.Entry.comparingByKey())
             .forEach(entry -> {
                 var  fs         = entry.getValue();
                 long restingKey = Math.round((fs.promoted() + fs.homeOffset()) / EPSILON_PUBLISH);
                 sb.append(entry.getKey()).append('=').append(restingKey).append(';');
             });
        return sb.toString();
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
            if (fs.promoted() != 0.0) {
                props.put(prefix + "promoted", String.valueOf(fs.promoted()));
            }
            if (fs.saturatedSince() != null) {
                props.put(prefix + "saturatedSince", fs.saturatedSince().toString());
            }
            if (fs.cappedSince() != null) {
                props.put(prefix + "cappedSince", fs.cappedSince().toString());
            }
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
        props.put("version", String.valueOf(state.version()));
        if (state.reflection() != null) {
            props.putAll(state.reflection().toProperties());
        }
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
                    Double.parseDouble(props.get(prefix + "load")),
                    parseDouble(props.get(prefix + "promoted"), 0.0),
                    parseInstant(props.get(prefix + "saturatedSince"), null),
                    parseInstant(props.get(prefix + "cappedSince"), null)));
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
                catch (IllegalArgumentException e) { LOG.fine(() -> "Unrecognised situational mode '" + winnerStr + "' in persisted state, skipping"); }
            }
        }
        var cursorTime = parseInstant(props.get("cursorTime"), fallbackNow);
        var hourCursor = parseInstant(props.get("hourCursor"), PersonalityState.truncateToHour(fallbackNow));
        var exposedUntil = parseInstant(props.get("exposedUntil"), Instant.EPOCH);
        var version = parseLong(props.get("version"), 0L);
        ReflectionLedger reflection = props.containsKey("jpaf.lastReflectionAt")
                ? ReflectionLedger.fromProperties(props, fallbackNow) : null;
        return new PersonalityState(facets, intensities, engaged, winners, cursorTime, hourCursor, exposedUntil, version, reflection);
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

    private static long parseLong(String v, long fallback) {
        if (v == null) {return fallback;}
        try {return Long.parseLong(v);} catch (NumberFormatException e) {return fallback;}
    }


}
