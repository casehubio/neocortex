package io.casehub.neocortex.memory.cbr.qdrant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.cbr.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deserializes {@link Memory} instances back to {@link CbrRecord} instances.
 * <p>
 * Inverse of {@link CbrMemorySerializer}.
 * <p>
 * Returns {@link Optional#empty()} on any deserialization failure (unknown cbrType,
 * malformed JSON, missing required attributes). Never throws.
 */
final class CbrMemoryDeserializer {
    private static final Logger LOG = Logger.getLogger(CbrMemoryDeserializer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE        = new TypeReference<>() {};
    private static final TypeReference<List<CbrPlanStep>>   PLAN_TRACE_TYPE = new TypeReference<>() {};

    private CbrMemoryDeserializer() {}

    static Optional<CbrRecord> deserialize(Memory memory) {
        try {
            String problem = memory.text();
            Map<String, String> attrs = memory.attributes();

            String solution = attrs.get(MemoryAttributeKeys.SOLUTION);
            if (solution == null) {
                LOG.warning("Missing solution attribute in memory " + memory.memoryId());
                return Optional.empty();
            }

            String outcome = attrs.get(MemoryAttributeKeys.OUTCOME);
            Double confidenceVal = attrs.containsKey(MemoryAttributeKeys.CONFIDENCE)
                ? MemoryAttributeKeys.parseConfidence(attrs.get(MemoryAttributeKeys.CONFIDENCE))
                : null;
            io.casehub.neocortex.cognitive.Confidence confidence = confidenceVal != null
                ? io.casehub.neocortex.cognitive.Confidence.unknown(confidenceVal) : null;

            String cbrType = attrs.get(CbrAttributeKeys.CBR_TYPE);
            if (cbrType == null) {
                LOG.warning("Missing cbr.type attribute in memory " + memory.memoryId());
                return Optional.empty();
            }

            CbrRecord result = switch (cbrType) {
                case CbrFeatureRecord.CBR_TYPE -> {
                    var rawFeatures = parseFeatures(attrs);
                    if (rawFeatures == null) yield null;
                    yield new CbrFeatureRecord(problem, solution, outcome, confidence, CbrPointBuilder.fromRawMap(rawFeatures), null, null);
                }
                case CbrPlanRecord.CBR_TYPE -> {
                    var rawFeatures = parseFeatures(attrs);
                    if (rawFeatures == null) yield null;
                    var               features    = CbrPointBuilder.fromRawMap(rawFeatures);
                    List<CbrPlanStep> cbrPlanStep = parsePlanTrace(attrs);
                    if (cbrPlanStep == null) yield null;
                    yield new CbrPlanRecord(problem, solution, outcome, confidence, features, cbrPlanStep, null, null);
                }
                case CbrGuidanceRecord.CBR_TYPE ->
                    new CbrGuidanceRecord(problem, solution, outcome, confidence, null, null);
                default -> {
                    LOG.warning("Unknown cbr.type '" + cbrType + "' in memory " + memory.memoryId());
                    yield null;
                }
            };

            if (result == null) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to deserialize memory " + memory.memoryId(), e);
            return Optional.empty();
        }
    }

    private static Map<String, Object> parseFeatures(Map<String, String> attrs) {
        String json = attrs.get(CbrAttributeKeys.CBR_FEATURES);
        if (json == null) return Map.of();
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            LOG.warning("Malformed cbr.features JSON: " + e.getMessage());
            return null;
        }
    }

    private static List<CbrPlanStep> parsePlanTrace(Map<String, String> attrs) {
        String json = attrs.get(CbrAttributeKeys.CBR_PLAN_TRACE);
        if (json == null) return List.of();
        try {
            return MAPPER.readValue(json, PLAN_TRACE_TYPE);
        } catch (JsonProcessingException e) {
            LOG.warning("Malformed cbr.planTrace JSON: " + e.getMessage());
            return null;
        }
    }
}
