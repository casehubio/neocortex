package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.mood.AffectEvents;
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

    DomainActivation(MindMapStore mindMapStore, CaseMemoryStore memoryStore) {
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
                            CorrelationStrength.NONE));
                    continue;
                }

                PadDtw.DtwResult dtw = PadDtw.compute(a, b);
                correlations.put(pair, new DomainCorrelation(
                        dtw.similarity(), dtw.alignment(),
                        Math.min(a.length, b.length),
                        CorrelationStrength.fromSimilarity(dtw.similarity())));
            }
        }

        return Optional.of(new DomainActivationResult(
                signals, correlations, query.principal(), query.tenantId(),
                query.from(), query.to()));
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