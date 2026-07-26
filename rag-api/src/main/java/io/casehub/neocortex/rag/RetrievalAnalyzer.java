package io.casehub.neocortex.rag;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RetrievalAnalyzer {

    private RetrievalAnalyzer() {}

    public static Map<String, DocumentStats> documentStats(
            RetrievalTracker tracker,
            CorpusRef corpus,
            Instant since, Instant until) {

        List<RetrievalRecord> records = tracker.findRecords(corpus, since, until);
        if (records.isEmpty()) {
            return Map.of();
        }

        List<RetrievalFeedback> allFeedback = tracker.findFeedback(corpus, since, Instant.MAX);

        Set<String> inWindowRetrievalIds = new HashSet<>();
        for (RetrievalRecord r : records) {
            inWindowRetrievalIds.add(r.retrievalId());
        }

        Map<String, Map<RetrievalOutcome, Integer>> feedbackByDoc = new HashMap<>();
        for (RetrievalFeedback fb : allFeedback) {
            if (inWindowRetrievalIds.contains(fb.retrievalId())) {
                feedbackByDoc
                        .computeIfAbsent(fb.sourceDocumentId(), k -> new EnumMap<>(RetrievalOutcome.class))
                        .merge(fb.outcome(), 1, Integer::sum);
            }
        }

        Map<String, List<DocAppearance>> appearances = new HashMap<>();
        for (RetrievalRecord r : records) {
            for (RetrievedDocumentRef doc : r.documents()) {
                appearances.computeIfAbsent(doc.sourceDocumentId(), k -> new ArrayList<>())
                        .add(new DocAppearance(r.timestamp(), doc.relevanceScore()));
            }
        }

        Map<String, DocumentStats> result = new LinkedHashMap<>();
        for (var entry : appearances.entrySet()) {
            String docId = entry.getKey();
            List<DocAppearance> apps = entry.getValue();

            int count = apps.size();
            Instant first = apps.stream().map(DocAppearance::timestamp).min(Comparator.naturalOrder()).orElseThrow();
            Instant last = apps.stream().map(DocAppearance::timestamp).max(Comparator.naturalOrder()).orElseThrow();
            double avgScore = apps.stream().mapToDouble(DocAppearance::score).average().orElse(0.0);
            Map<RetrievalOutcome, Integer> dist = feedbackByDoc.getOrDefault(docId, Map.of());

            result.put(docId, new DocumentStats(docId, count, first, last, avgScore, dist));
        }

        return result;
    }

    public static Set<String> unretrievedDocuments(
            RetrievalTracker tracker,
            EmbeddingIngestor ingestor,
            CorpusRef corpus,
            Instant since, Instant until) {

        Set<String>  retrieved    = tracker.findRetrievedDocumentIds(corpus, since, until);
        List<String> allDocuments = ingestor.listDocuments(corpus);

        Set<String> unretrieved = new LinkedHashSet<>();
        for (String docId : allDocuments) {
            if (!retrieved.contains(docId)) {
                unretrieved.add(docId);
            }
        }
        return unretrieved;
    }

    public static List<DocumentQualitySignal> qualitySignals(
            RetrievalTracker tracker,
            EmbeddingIngestor ingestor,
            CorpusRef corpus,
            Instant since, Instant until,
            QualityThresholds thresholds) {

        Map<String, DocumentStats> stats       = documentStats(tracker, corpus, since, until);
        Set<String>                unretrieved = unretrievedDocuments(tracker, ingestor, corpus, since, until);

        List<DocumentQualitySignal> neverRetrievedSignals = new ArrayList<>();
        List<DocumentQualitySignal> lowQualitySignals     = new ArrayList<>();
        List<DocumentQualitySignal> staleSignals          = new ArrayList<>();

        for (String docId : unretrieved) {
            neverRetrievedSignals.add(
                    new DocumentQualitySignal(docId, null, QualitySignal.NEVER_RETRIEVED));
        }

        Instant staleCutoff = until.minus(thresholds.staleWindow());

        for (var entry : stats.entrySet()) {
            String        docId = entry.getKey();
            DocumentStats ds    = entry.getValue();

            if (ds.retrievalCount() >= thresholds.minRetrievalsForQualityCheck()) {
                int totalFeedback = ds.feedbackDistribution().values().stream()
                                      .mapToInt(Integer::intValue).sum();
                if (totalFeedback >= thresholds.minFeedbackForQualityCheck()) {
                    int lowCount = ds.feedbackDistribution()
                                     .getOrDefault(RetrievalOutcome.NOT_RELEVANT, 0)
                                   + ds.feedbackDistribution()
                                       .getOrDefault(RetrievalOutcome.PARTIALLY_RELEVANT, 0);
                    double ratio = (double) lowCount / totalFeedback;
                    if (ratio >= thresholds.lowQualityRatio()) {
                        lowQualitySignals.add(
                                new DocumentQualitySignal(docId, ds,
                                                          QualitySignal.HIGH_RETRIEVAL_LOW_QUALITY));
                        continue;
                    }
                }
            }

            if (ds.lastRetrieved().isBefore(staleCutoff)) {
                staleSignals.add(
                        new DocumentQualitySignal(docId, ds, QualitySignal.STALE));
            }
        }

        List<DocumentQualitySignal> result = new ArrayList<>(
                neverRetrievedSignals.size() + lowQualitySignals.size() + staleSignals.size());
        result.addAll(neverRetrievedSignals);
        result.addAll(lowQualitySignals);
        result.addAll(staleSignals);
        return result;
    }

    public static List<QueryQualitySignal> lowRelevanceQueries(
            RetrievalTracker tracker, CorpusRef corpus,
            Instant since, Instant until, double scoreThreshold) {

        List<RetrievalRecord> records = tracker.findRecords(corpus, since, until);
        if (records.isEmpty()) {return List.of();}

        Map<String, List<Double>> scoresByQuery   = new HashMap<>();
        Map<String, Instant>      lastSeenByQuery = new HashMap<>();

        for (RetrievalRecord r : records) {
            String qt = r.query().text();
            if (r.documents().isEmpty()) {continue;}
            double avg = r.documents().stream()
                          .mapToDouble(RetrievedDocumentRef::relevanceScore).average().orElse(0.0);
            scoresByQuery.computeIfAbsent(qt, k -> new ArrayList<>()).add(avg);
            lastSeenByQuery.merge(qt, r.timestamp(),
                                  (a, b) -> a.isAfter(b) ? a : b);
        }

        List<QueryQualitySignal> result = new ArrayList<>();
        for (var entry : scoresByQuery.entrySet()) {
            List<Double> scores     = entry.getValue();
            double       overallAvg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            if (overallAvg < scoreThreshold) {
                result.add(new QueryQualitySignal(
                        entry.getKey(), overallAvg, scores.size(), lastSeenByQuery.get(entry.getKey())));
            }
        }
        result.sort(Comparator.comparingDouble(QueryQualitySignal::averageRelevanceScore));
        return result;
    }

    public static List<QueryQualitySignal> zeroHitQueries(
            RetrievalTracker tracker, CorpusRef corpus,
            Instant since, Instant until) {

        List<RetrievalRecord> records = tracker.findRecords(corpus, since, until);
        if (records.isEmpty()) {return List.of();}

        Map<String, Integer> countByQuery    = new LinkedHashMap<>();
        Map<String, Instant> lastSeenByQuery = new HashMap<>();

        for (RetrievalRecord r : records) {
            if (r.documents().isEmpty()) {
                String qt = r.query().text();
                countByQuery.merge(qt, 1, Integer::sum);
                lastSeenByQuery.merge(qt, r.timestamp(),
                                      (a, b) -> a.isAfter(b) ? a : b);
            }
        }

        List<QueryQualitySignal> result = new ArrayList<>();
        for (var entry : countByQuery.entrySet()) {
            result.add(new QueryQualitySignal(
                    entry.getKey(), 0.0, entry.getValue(), lastSeenByQuery.get(entry.getKey())));
        }
        return result;
    }

    public static Map<String, QueryFrequencyStats> queryFrequency(
            RetrievalTracker tracker, CorpusRef corpus,
            Instant since, Instant until) {

        List<RetrievalRecord> records = tracker.findRecords(corpus, since, until);
        if (records.isEmpty()) {return Map.of();}

        Map<String, Integer>      countByQuery     = new LinkedHashMap<>();
        Map<String, List<Double>> scoresByQuery    = new HashMap<>();
        Map<String, Instant>      firstSeenByQuery = new HashMap<>();
        Map<String, Instant>      lastSeenByQuery  = new HashMap<>();

        for (RetrievalRecord r : records) {
            String qt = r.query().text();
            countByQuery.merge(qt, 1, Integer::sum);
            double avg = r.documents().isEmpty() ? 0.0
                                                 : r.documents().stream()
                                                    .mapToDouble(RetrievedDocumentRef::relevanceScore).average().orElse(0.0);
            scoresByQuery.computeIfAbsent(qt, k -> new ArrayList<>()).add(avg);
            firstSeenByQuery.merge(qt, r.timestamp(),
                                   (a, b) -> a.isBefore(b) ? a : b);
            lastSeenByQuery.merge(qt, r.timestamp(),
                                  (a, b) -> a.isAfter(b) ? a : b);
        }

        Map<String, QueryFrequencyStats> result = new LinkedHashMap<>();
        countByQuery.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEachOrdered(entry -> {
                        String qt = entry.getKey();
                        double avgScore = scoresByQuery.get(qt).stream()
                                                       .mapToDouble(Double::doubleValue).average().orElse(0.0);
                        result.put(qt, new QueryFrequencyStats(
                                entry.getValue(), avgScore,
                                firstSeenByQuery.get(qt), lastSeenByQuery.get(qt)));
                    });
        return result;
    }

    public static CorrelationGraph correlationGraph(
            RetrievalTracker tracker, CorpusRef corpus,
            Instant since, Instant until) {

        List<RetrievalRecord>   records     = tracker.findRecords(corpus, since, until);
        List<RetrievalFeedback> allFeedback = tracker.findFeedback(corpus, since, until);

        if (records.isEmpty()) {
            return new CorrelationGraph(Map.of(), Map.of());
        }

        Set<String> inWindowRetrievalIds = new HashSet<>();
        for (RetrievalRecord r : records) {
            inWindowRetrievalIds.add(r.retrievalId());
        }

        Map<String, List<RetrievalOutcome>> feedbackIndex = new HashMap<>();
        for (RetrievalFeedback fb : allFeedback) {
            if (inWindowRetrievalIds.contains(fb.retrievalId())) {
                feedbackIndex.computeIfAbsent(
                        fb.retrievalId() + "\0" + fb.sourceDocumentId(),
                        k -> new ArrayList<>()).add(fb.outcome());
            }
        }

        Map<String, Integer>                                     queryRetrievalCount = new HashMap<>();
        Map<String, Map<String, List<Double>>>                   queryDocScores      = new HashMap<>();
        Map<String, Map<String, Map<RetrievalOutcome, Integer>>> queryDocOutcomes    = new HashMap<>();

        for (RetrievalRecord r : records) {
            String qKey = r.query().text().strip().toLowerCase();
            queryRetrievalCount.merge(qKey, 1, Integer::sum);

            for (RetrievedDocumentRef doc : r.documents()) {
                queryDocScores
                        .computeIfAbsent(qKey, k -> new HashMap<>())
                        .computeIfAbsent(doc.sourceDocumentId(), k -> new ArrayList<>())
                        .add(doc.relevanceScore());

                String                 fbKey    = r.retrievalId() + "\0" + doc.sourceDocumentId();
                List<RetrievalOutcome> outcomes = feedbackIndex.getOrDefault(fbKey, List.of());
                for (RetrievalOutcome outcome : outcomes) {
                    queryDocOutcomes
                            .computeIfAbsent(qKey, k -> new HashMap<>())
                            .computeIfAbsent(doc.sourceDocumentId(), k -> new EnumMap<>(RetrievalOutcome.class))
                            .merge(outcome, 1, Integer::sum);
                }
            }
        }

        Map<String, Map<String, EdgeStats>> queryEdgeMap = new LinkedHashMap<>();
        for (var qEntry : queryDocScores.entrySet()) {
            String                 qKey  = qEntry.getKey();
            Map<String, EdgeStats> edges = new LinkedHashMap<>();
            for (var dEntry : qEntry.getValue().entrySet()) {
                String       docId  = dEntry.getKey();
                List<Double> scores = dEntry.getValue();
                int          count  = scores.size();
                double       avg    = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                Map<RetrievalOutcome, Integer> dist = queryDocOutcomes
                                                              .getOrDefault(qKey, Map.of())
                                                              .getOrDefault(docId, Map.of());
                edges.put(docId, new EdgeStats(count, avg, dist));
            }
            queryEdgeMap.put(qKey, edges);
        }

        Map<String, QueryNode> queryNodes = new LinkedHashMap<>();
        for (var entry : queryEdgeMap.entrySet()) {
            String qKey = entry.getKey();
            queryNodes.put(qKey, new QueryNode(
                    qKey, queryRetrievalCount.get(qKey), entry.getValue()));
        }

        Map<String, Map<String, EdgeStats>> docEdgeMap        = new LinkedHashMap<>();
        Map<String, Integer>                docRetrievalCount = new HashMap<>();
        for (var qEntry : queryEdgeMap.entrySet()) {
            String qKey = qEntry.getKey();
            for (var dEntry : qEntry.getValue().entrySet()) {
                String docId = dEntry.getKey();
                docEdgeMap.computeIfAbsent(docId, k -> new LinkedHashMap<>())
                          .put(qKey, dEntry.getValue());
                docRetrievalCount.merge(docId,
                                        dEntry.getValue().coOccurrenceCount(), Integer::sum);
            }
        }

        Map<String, DocumentNode> documentNodes = new LinkedHashMap<>();
        for (var entry : docEdgeMap.entrySet()) {
            documentNodes.put(entry.getKey(), new DocumentNode(
                    entry.getKey(), docRetrievalCount.get(entry.getKey()),
                    entry.getValue()));
        }

        return new CorrelationGraph(queryNodes, documentNodes);
    }

    public static List<QueryCluster> queryClusters(
            CorrelationGraph graph, double jaccardThreshold) {

        List<String> queryKeys = new ArrayList<>(graph.queries().keySet());
        int          n         = queryKeys.size();
        if (n < 2) {return List.of();}

        Map<String, Set<String>> docSets = new HashMap<>();
        for (var entry : graph.queries().entrySet()) {
            docSets.put(entry.getKey(), entry.getValue().documentEdges().keySet());
        }

        Map<String, Set<String>> adj = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String qi  = queryKeys.get(i), qj = queryKeys.get(j);
                double sim = jaccard(docSets.get(qi), docSets.get(qj));
                if (sim >= jaccardThreshold) {
                    adj.computeIfAbsent(qi, k -> new HashSet<>()).add(qj);
                    adj.computeIfAbsent(qj, k -> new HashSet<>()).add(qi);
                }
            }
        }

        Set<String>        visited  = new HashSet<>();
        List<QueryCluster> clusters = new ArrayList<>();
        for (String q : queryKeys) {
            if (visited.contains(q) || !adj.containsKey(q)) {continue;}
            Set<String>   component = new LinkedHashSet<>();
            Deque<String> queue     = new ArrayDeque<>();
            queue.add(q);
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                if (!visited.add(cur)) {continue;}
                component.add(cur);
                Set<String> neighbors = adj.getOrDefault(cur, Set.of());
                for (String nb : neighbors) {
                    if (!visited.contains(nb)) {queue.add(nb);}
                }
            }
            if (component.size() >= 2) {
                double       minSim  = 1.0;
                List<String> members = new ArrayList<>(component);
                for (int i = 0; i < members.size(); i++) {
                    for (int j = i + 1; j < members.size(); j++) {
                        double sim = jaccard(docSets.get(members.get(i)),
                                             docSets.get(members.get(j)));
                        minSim = Math.min(minSim, sim);
                    }
                }
                Set<String> shared = new HashSet<>(docSets.get(members.get(0)));
                for (int i = 1; i < members.size(); i++) {
                    shared.retainAll(docSets.get(members.get(i)));
                }
                clusters.add(new QueryCluster(component, minSim, shared));
            }
        }
        clusters.sort(Comparator.comparingDouble(QueryCluster::jaccardSimilarity).reversed());
        return clusters;
    }

    public static List<DocumentImpact> documentImpact(CorrelationGraph graph) {
        List<DocumentImpact> result = new ArrayList<>();
        for (var entry : graph.documents().entrySet()) {
            DocumentNode node            = entry.getValue();
            int          distinctQueries = node.queryEdges().size();
            int totalRetrievals = node.queryEdges().values().stream()
                                      .mapToInt(EdgeStats::coOccurrenceCount).sum();
            double avgScore = node.queryEdges().values().stream()
                                  .mapToDouble(e -> e.averageScore() * e.coOccurrenceCount())
                                  .sum() / totalRetrievals;

            Map<RetrievalOutcome, Integer> aggregated = new EnumMap<>(RetrievalOutcome.class);
            for (EdgeStats edge : node.queryEdges().values()) {
                for (var oe : edge.outcomeDistribution().entrySet()) {
                    aggregated.merge(oe.getKey(), oe.getValue(), Integer::sum);
                }
            }

            result.add(new DocumentImpact(
                    entry.getKey(), distinctQueries, totalRetrievals,
                    avgScore, aggregated));
        }
        result.sort(Comparator.comparingInt(DocumentImpact::distinctQueryCount).reversed());
        return result;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {return 0.0;}
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }


    private record DocAppearance(Instant timestamp, double score) {}
}
