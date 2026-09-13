package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.cbr.WarpingConstraint;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.mood.AffectEvents;
import io.casehub.neocortex.memory.mood.MoodAttributeKeys;
import io.casehub.neocortex.memory.mood.MoodEvents;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class DomainActivation {

    private final MindMapStore    mindMapStore;
    private final CaseMemoryStore memoryStore;

    @Inject
    public DomainActivation(Instance<MindMapStore> mindMapStore,
                            Instance<CaseMemoryStore> memoryStore) {
        this.mindMapStore = mindMapStore != null && mindMapStore.isResolvable()
                            ? mindMapStore.get() : null;
        this.memoryStore  = memoryStore != null && memoryStore.isResolvable()
                            ? memoryStore.get() : null;
    }

    public DomainActivation(MindMapStore mindMapStore, CaseMemoryStore memoryStore) {
        this.mindMapStore = mindMapStore;
        this.memoryStore  = memoryStore;
    }

    public Optional<DomainActivationResult> correlate(DomainActivationQuery query) {
        if (mindMapStore == null || memoryStore == null) {
            return Optional.empty();
        }

        Map<String, DomainSignal> signals   = new LinkedHashMap<>();
        Map<String, double[][]>   padSeries = new LinkedHashMap<>();

        for (String sgId : query.subgraphIds()) {
            List<MindMapNode> entities = mindMapStore.search(
                    MindMapQuery.of(query.tenantId(), 1000).withSubgraphId(sgId));
            if (entities.isEmpty()) {
                return Optional.empty();
            }

            List<Memory> allMemories = new ArrayList<>();
            for (MindMapNode entity : entities) {
                var memQuery = MemoryQuery.forSubjects(
                                                  List.of(Subject.of("unknown", entity.id()),
                                                          Subject.of("unknown", entity.name())),
                                                  AffectEvents.DOMAIN, query.tenantId())
                                          .withCallerPrincipalId(query.principal())
                                          .withLimit(1000)
                                          .withOrder(MemoryOrder.CHRONOLOGICAL);
                if (query.from() != null) {
                    memQuery = memQuery.withSince(query.from());
                }
                allMemories.addAll(memoryStore.query(memQuery));
            }

            if (query.to() != null) {
                allMemories.removeIf(m -> m.createdAt() != null && m.createdAt().isAfter(query.to()));
            }

            if (allMemories.isEmpty()) {
                return Optional.empty();
            }

            allMemories.sort(Comparator.comparing(
                    m -> m.createdAt() != null ? m.createdAt() : Instant.EPOCH));

            AffectTrajectory trajectory = AffectTrajectoryAnalyzer.analyze(allMemories);
            double[][] buckets = timeBucket(allMemories, query.bucketDuration(),
                                            query.from(), query.to());

            signals.put(sgId, new DomainSignal(sgId, trajectory,
                                               entities.size(), allMemories.size(), buckets.length));
            padSeries.put(sgId, buckets);
        }

        List<String>                       sgIds        = new ArrayList<>(query.subgraphIds());
        Map<DomainPair, DomainCorrelation> correlations = new LinkedHashMap<>();
        for (int i = 0; i < sgIds.size(); i++) {
            for (int j = i + 1; j < sgIds.size(); j++) {
                DomainPair pair = new DomainPair(sgIds.get(i), sgIds.get(j));
                double[][] a    = padSeries.get(sgIds.get(i));
                double[][] b    = padSeries.get(sgIds.get(j));

                if (a.length < 2 || b.length < 2) {
                    correlations.put(pair, new DomainCorrelation(
                            0.0, List.of(), Math.min(a.length, b.length),
                            CorrelationStrength.NONE, Double.NaN, 0, 0));
                    continue;
                }

                PadDtw.DtwResult dtw = PadDtw.compute(a, b);
                correlations.put(pair, new DomainCorrelation(
                        dtw.similarity(), dtw.alignment(),
                        Math.min(a.length, b.length),
                        CorrelationStrength.fromSimilarity(dtw.similarity()),
                        Double.NaN, 0, 0));
            }
        }

        Map<MemoryDomain, Map<String, DomainCorrelation>> contextCorrelations = new LinkedHashMap<>();
        Map<MemoryDomain, Map<String, EventImpact>> eventImpacts = new LinkedHashMap<>();

        if (!query.contextDomains().isEmpty()) {
            for (MemoryDomain domain : query.contextDomains()) {
                if (MoodEvents.DOMAIN.equals(domain)) {
                    contextCorrelations.put(domain, correlateMood(query, sgIds, padSeries));
                } else if (ExperienceEvents.DOMAIN.equals(domain)) {
                    eventImpacts.put(domain, correlateExperience(query, sgIds));
                }
            }
        }

        return Optional.of(new DomainActivationResult(
                signals, correlations, contextCorrelations, eventImpacts,
                query.principal(), query.tenantId(),
                query.from(), query.to()));
    }

    private Map<String, DomainCorrelation> correlateMood(
            DomainActivationQuery query, List<String> sgIds,
            Map<String, double[][]> affectSeries) {

        var moodQuery = MemoryQuery.forSubjects(
                            List.of(Subject.of("agent", query.principal().id())),
                            MoodEvents.DOMAIN, query.tenantId())
                        .withCallerPrincipalId(query.principal())
                        .withLimit(1000)
                        .withOrder(MemoryOrder.CHRONOLOGICAL);
        if (query.from() != null) moodQuery = moodQuery.withSince(query.from());
        List<Memory> allMood = new ArrayList<>(memoryStore.query(moodQuery));
        if (query.to() != null) {
            allMood.removeIf(m -> m.createdAt() != null && m.createdAt().isAfter(query.to()));
        }

        Map<String, DomainCorrelation> results = new LinkedHashMap<>();
        for (String sgId : sgIds) {
            List<Memory> partitioned = partitionMoodByContext(allMood, sgId);
            if (partitioned.isEmpty() || !affectSeries.containsKey(sgId)) {
                results.put(sgId, new DomainCorrelation(0.0, List.of(), 0,
                        CorrelationStrength.NONE, Double.NaN, 0, 0));
                continue;
            }

            int attributed = (int) partitioned.stream()
                    .filter(m -> m.attributes() != null
                            && m.attributes().containsKey(MoodAttributeKeys.ACTIVE_CONTEXT_IDS))
                    .count();

            double[][] moodBuckets = timeBucket(partitioned, query.bucketDuration(),
                                                query.from(), query.to());
            double[][] affectBuckets = affectSeries.get(sgId);

            if (moodBuckets.length < 2 || affectBuckets.length < 2) {
                results.put(sgId, new DomainCorrelation(0.0, List.of(),
                        Math.min(moodBuckets.length, affectBuckets.length),
                        CorrelationStrength.NONE, Double.NaN, attributed, partitioned.size()));
                continue;
            }

            int bandWidth = Math.max(1, (int)(Math.max(moodBuckets.length, affectBuckets.length) * 0.1));
            var constraint = new WarpingConstraint.SakoeChibaBand(bandWidth);

            var sig = PadDtw.significanceTest(moodBuckets, affectBuckets,
                                              constraint, 200, query.hashCode());
            results.put(sgId, new DomainCorrelation(
                    sig.similarity(), List.of(),
                    Math.min(moodBuckets.length, affectBuckets.length),
                    sig.strength(), sig.pValue(),
                    attributed, partitioned.size()));
        }
        return results;
    }

    private List<Memory> partitionMoodByContext(List<Memory> allMood, String sgId) {
        List<Memory> result = new ArrayList<>();
        for (Memory m : allMood) {
            String contextIds = m.attributes() != null
                    ? m.attributes().get(MoodAttributeKeys.ACTIVE_CONTEXT_IDS) : null;
            if (contextIds == null || contextIds.isEmpty()) {
                result.add(m);
            } else if (new java.util.HashSet<>(java.util.Arrays.asList(contextIds.split(","))).contains(sgId)) {
                result.add(m);
            }
        }
        return result;
    }

    private Map<String, EventImpact> correlateExperience(
            DomainActivationQuery query, List<String> sgIds) {

        var expQuery = MemoryQuery.forSubjects(
                           List.of(Subject.of("agent", query.principal().id())),
                           ExperienceEvents.DOMAIN, query.tenantId())
                       .withCallerPrincipalId(query.principal())
                       .withLimit(1000)
                       .withOrder(MemoryOrder.CHRONOLOGICAL);
        if (query.from() != null) expQuery = expQuery.withSince(query.from());
        List<Memory> experiences = new ArrayList<>(memoryStore.query(expQuery));
        if (query.to() != null) {
            experiences.removeIf(m -> m.createdAt() != null && m.createdAt().isAfter(query.to()));
        }

        if (experiences.isEmpty()) return Map.of();

        Duration window = query.eventWindow() != null ? query.eventWindow() : query.bucketDuration();

        Map<String, EventImpact> results = new LinkedHashMap<>();
        for (String sgId : sgIds) {
            List<MindMapNode> entities = mindMapStore.search(
                    MindMapQuery.of(query.tenantId(), 1000).withSubgraphId(sgId));

            List<Memory> affectForSubgraph = new ArrayList<>();
            for (MindMapNode entity : entities) {
                var memQuery = MemoryQuery.forSubjects(
                        List.of(Subject.of("unknown", entity.id()),
                                Subject.of("unknown", entity.name())),
                        AffectEvents.DOMAIN, query.tenantId())
                    .withCallerPrincipalId(query.principal())
                    .withLimit(1000)
                    .withOrder(MemoryOrder.CHRONOLOGICAL);
                if (query.from() != null) {
                    memQuery = memQuery.withSince(query.from());
                }
                affectForSubgraph.addAll(memoryStore.query(memQuery));
            }
            if (query.to() != null) {
                affectForSubgraph.removeIf(m -> m.createdAt() != null
                        && m.createdAt().isAfter(query.to()));
            }
            affectForSubgraph.sort(Comparator.comparing(
                    m -> m.createdAt() != null ? m.createdAt() : Instant.EPOCH));

            results.put(sgId, EventTriggeredAnalyzer.analyze(
                    experiences, affectForSubgraph, window));
        }
        return results;
    }

    private double[][] timeBucket(List<Memory> memories, Duration bucket,
                                  Instant from, Instant to) {
        if (memories.isEmpty()) {
            return new double[0][];
        }
        Instant start = from != null ? from
                                     : (memories.getFirst().createdAt() != null ? memories.getFirst().createdAt() : Instant.EPOCH);
        Instant end = to != null ? to
                                 : (memories.getLast().createdAt() != null ? memories.getLast().createdAt() : Instant.EPOCH);
        long bucketMs    = bucket.toMillis();
        int  bucketCount = (int) ((end.toEpochMilli() - start.toEpochMilli()) / bucketMs) + 1;
        bucketCount = Math.max(1, Math.min(bucketCount, 10000));

        double[][] sums   = new double[bucketCount][3];
        int[]      counts = new int[bucketCount];
        for (Memory m : memories) {
            if (m.createdAt() == null) {continue;}
            int idx = (int) ((m.createdAt().toEpochMilli() - start.toEpochMilli()) / bucketMs);
            idx = Math.max(0, Math.min(idx, bucketCount - 1));
            sums[idx][0] += m.pleasure() != null ? m.pleasure() : 0.0;
            sums[idx][1] += m.arousal() != null ? m.arousal() : 0.0;
            sums[idx][2] += m.dominance() != null ? m.dominance() : 0.0;
            counts[idx]++;
        }

        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            if (counts[i] > 0) {
                result.add(new double[]{
                        sums[i][0] / counts[i],
                        sums[i][1] / counts[i],
                        sums[i][2] / counts[i]
                });
            }
        }
        return result.toArray(new double[0][]);
    }
}