package io.casehub.neocortex.memory.cbr.qdrant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializes {@link CbrRecord} instances to {@link MemoryInput} for storage
 * in {@link io.casehub.neocortex.memory.CaseMemoryStore}.
 * <p>
 * Inverse of {@link CbrMemoryDeserializer}.
 */
final class CbrMemorySerializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private CbrMemorySerializer() {}

    static MemoryInput serialize(CbrRecord cbrRecord, String entityId,
                                 MemoryDomain domain, String tenantId,
                                 String caseId, String caseType) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(MemoryAttributeKeys.SOLUTION, cbrRecord.solution());
        if (cbrRecord.outcome() != null) {
            attributes.put(MemoryAttributeKeys.OUTCOME, cbrRecord.outcome());
        }
        if (cbrRecord.confidence() != null) {
            attributes.put(MemoryAttributeKeys.CONFIDENCE,
                MemoryAttributeKeys.formatConfidence(cbrRecord.confidence().value()));
        }

        attributes.put(CbrAttributeKeys.CBR_TYPE, cbrRecord.recordType());
        Map<String, Object> features = CbrPointBuilder.toRawMap(cbrRecord.features());
        if (!features.isEmpty()) {
            try {
                attributes.put(CbrAttributeKeys.CBR_FEATURES, MAPPER.writeValueAsString(features));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize features to JSON", e);
            }
        }
        if (cbrRecord instanceof CbrPlanRecord plan) {
            try {
                attributes.put(CbrAttributeKeys.CBR_PLAN_TRACE, MAPPER.writeValueAsString(plan.cbrPlanStep()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize plan trace to JSON", e);
            }
        }
        attributes.put(CbrAttributeKeys.CBR_CASE_TYPE, caseType);

        return new MemoryInput(entityId, domain, tenantId, caseId, cbrRecord.problem(), attributes, null, null, null, null);
    }
}
