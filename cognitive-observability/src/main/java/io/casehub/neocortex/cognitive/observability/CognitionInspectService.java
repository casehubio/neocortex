package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CognitionInspectService {

    private CognitionInspectService() {}

    public static CognitionInspectResult inspect(MindMapStore store, String tenantId, String subgraphId) {
        List<MindMapSubgraph> subgraphs;
        if (subgraphId != null) {
            var sg = store.getSubgraph(subgraphId, tenantId);
            subgraphs = sg != null ? List.of(sg) : List.of();
        } else {
            subgraphs = store.listSubgraphs(tenantId);
        }

        List<CognitionInspectResult.SubgraphStat> stats = new ArrayList<>();
        Map<String, Integer> histogram = new LinkedHashMap<>();
        histogram.put("0.0-0.2", 0);
        histogram.put("0.2-0.4", 0);
        histogram.put("0.4-0.6", 0);
        histogram.put("0.6-0.8", 0);
        histogram.put("0.8-1.0", 0);

        for (var sg : subgraphs) {
            List<MindMapNode> nodes = store.nodesIn(sg.id(), tenantId);
            int edgeCount = 0;
            double confidenceSum = 0;
            Map<String, Long> traitDist = new HashMap<>();

            for (var node : nodes) {
                edgeCount += store.neighbors(node.id(), tenantId).size();
                Confidence conf = node.confidence();
                double confVal = conf != null ? conf.value() : 0.0;
                confidenceSum += confVal;
                bucketConfidence(histogram, confVal);

                if (node.traits() != null) {
                    for (String trait : node.traits()) {
                        traitDist.merge(trait, 1L, Long::sum);
                    }
                }
            }

            // edges are counted from both endpoints — deduplicate by halving
            int dedupedEdges = edgeCount / 2;
            double avgConf = nodes.isEmpty() ? 0.0 : confidenceSum / nodes.size();

            stats.add(new CognitionInspectResult.SubgraphStat(
                sg.id(), sg.type(), nodes.size(), dedupedEdges, avgConf, traitDist));
        }

        return new CognitionInspectResult(stats, histogram);
    }

    private static void bucketConfidence(Map<String, Integer> histogram, double value) {
        String bucket;
        if (value < 0.2) bucket = "0.0-0.2";
        else if (value < 0.4) bucket = "0.2-0.4";
        else if (value < 0.6) bucket = "0.4-0.6";
        else if (value < 0.8) bucket = "0.6-0.8";
        else bucket = "0.8-1.0";
        histogram.merge(bucket, 1, Integer::sum);
    }
}
