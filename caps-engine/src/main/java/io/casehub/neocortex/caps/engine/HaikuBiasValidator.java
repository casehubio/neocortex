package io.casehub.neocortex.caps.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.TrajectoryEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Standalone log consumer that reads classification-audit.jsonl entries
 * and independently validates inline classifications for persona bias.
 * Uses a persistent Haiku session (managed externally) to classify the
 * same trajectory text, then computes divergence metrics.
 */
public class HaikuBiasValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record AuditEntry(
        int tick,
        String agentId,
        String mode,
        List<TrajectoryEntry> trajectory,
        List<SituationActivation> activations,
        String reasoning
    ) {}

    public record DivergenceResult(
        double jaccardDivergence,
        double meanConfidenceDelta,
        Set<String> sharedNodes
    ) {}

    public static List<AuditEntry> parseAuditFile(Path auditFile) throws IOException {
        var entries = new ArrayList<AuditEntry>();
        for (String line : Files.readAllLines(auditFile)) {
            if (line.isBlank()) continue;
            JsonNode root = MAPPER.readTree(line);

            var trajectory = new ArrayList<TrajectoryEntry>();
            for (JsonNode t : root.path("trajectory")) {
                trajectory.add(new TrajectoryEntry(t.get("tick").asInt(), t.get("text").asText()));
            }

            var activations = new ArrayList<SituationActivation>();
            for (JsonNode a : root.path("activations")) {
                activations.add(new SituationActivation(a.get("node").asText(), a.get("confidence").asDouble()));
            }

            entries.add(new AuditEntry(
                root.get("tick").asInt(),
                root.get("agentId").asText(),
                root.get("mode").asText(),
                List.copyOf(trajectory),
                List.copyOf(activations),
                root.path("reasoning").asText("")
            ));
        }
        return entries;
    }

    public static DivergenceResult computeDivergence(
            List<SituationActivation> inlineActivations,
            List<SituationActivation> haikuActivations) {

        Set<String> inlineNodes = inlineActivations.stream()
            .map(SituationActivation::nodeId).collect(Collectors.toSet());
        Set<String> haikuNodes = haikuActivations.stream()
            .map(SituationActivation::nodeId).collect(Collectors.toSet());

        if (inlineNodes.isEmpty() && haikuNodes.isEmpty()) {
            return new DivergenceResult(0.0, 0.0, Set.of());
        }

        Set<String> intersection = new HashSet<>(inlineNodes);
        intersection.retainAll(haikuNodes);

        Set<String> union = new HashSet<>(inlineNodes);
        union.addAll(haikuNodes);

        double jaccardSimilarity = union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();

        Map<String, Double> inlineMap = inlineActivations.stream()
            .collect(Collectors.toMap(SituationActivation::nodeId, SituationActivation::confidence, Math::max));
        Map<String, Double> haikuMap = haikuActivations.stream()
            .collect(Collectors.toMap(SituationActivation::nodeId, SituationActivation::confidence, Math::max));

        double confidenceDeltaSum = 0.0;
        for (String node : intersection) {
            confidenceDeltaSum += Math.abs(inlineMap.getOrDefault(node, 0.0) - haikuMap.getOrDefault(node, 0.0));
        }
        double meanDelta = intersection.isEmpty() ? 0.0 : confidenceDeltaSum / intersection.size();

        return new DivergenceResult(1.0 - jaccardSimilarity, meanDelta, Set.copyOf(intersection));
    }
}
