package io.casehub.neocortex.memory.cbr.qdrant;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.*;
import io.casehub.neocortex.memory.cbr.*;
import static io.casehub.neocortex.memory.cbr.FeatureValue.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class CbrMemoryDeserializerTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");
    private static final String TENANT = "test-tenant";
    private static final String ENTITY = "test-entity";

    @Test
    void roundTrip_featureRecord() {
        var original = new CbrFeatureRecord("Zerg rush detected", "wall-off and expand",
                                            "WIN", Confidence.unknown(0.85), Map.of("opponent_race", string("Zerg"), "army_size_ratio", number(0.7)), null, null);

        var deserialized = roundTrip(original, "starcraft-game");

        assertThat(deserialized).isPresent();
        assertThat(deserialized.get()).isInstanceOf(CbrFeatureRecord.class);
        var fv = (CbrFeatureRecord) deserialized.get();
        assertThat(fv.problem()).isEqualTo("Zerg rush detected");
        assertThat(fv.solution()).isEqualTo("wall-off and expand");
        assertThat(fv.outcome()).isEqualTo("WIN");
        assertThat(fv.confidence().value()).isEqualTo(0.85);
        assertThat(fv.features()).containsEntry("opponent_race", string("Zerg"));
    }

    @Test
    void roundTrip_planRecord() {
        var trace = new CbrPlanStep("scout", "reconnaissance", "drone-scout", "SUCCESS", 1,
                                    Map.of("duration", 30), null);
        var original = new CbrPlanRecord("Zerg rush", "early pressure", "WIN", Confidence.unknown(0.9),
                                         Map.of("opponent_race", string("Zerg")), List.of(trace), null, null);

        var deserialized = roundTrip(original, "starcraft-game");

        assertThat(deserialized).isPresent();
        assertThat(deserialized.get()).isInstanceOf(CbrPlanRecord.class);
        var plan = (CbrPlanRecord) deserialized.get();
        assertThat(plan.problem()).isEqualTo("Zerg rush");
        assertThat(plan.cbrPlanStep()).hasSize(1);
        assertThat(plan.cbrPlanStep().get(0).bindingName()).isEqualTo("scout");
    }

    @Test
    void roundTrip_guidanceRecord() {
        var original = new CbrGuidanceRecord("simple problem", "simple solution", "OK", Confidence.unknown(0.5), null, null);

        var deserialized = roundTrip(original, "simple-type");

        assertThat(deserialized).isPresent();
        assertThat(deserialized.get()).isInstanceOf(CbrGuidanceRecord.class);
        var t = (CbrGuidanceRecord) deserialized.get();
        assertThat(t.problem()).isEqualTo("simple problem");
        assertThat(t.solution()).isEqualTo("simple solution");
        assertThat(t.outcome()).isEqualTo("OK");
        assertThat(t.confidence().value()).isEqualTo(0.5);
    }

    @Test
    void roundTrip_nullOptionalFields() {
        var original = new CbrGuidanceRecord("p", "s", null, null, null, null);

        var deserialized = roundTrip(original, "minimal");

        assertThat(deserialized).isPresent();
        assertThat(deserialized.get().outcome()).isNull();
        assertThat(deserialized.get().confidence()).isNull();
    }

    @Test
    void deserialize_unknownCbrType_returnsEmpty() {
        var memory = new Memory("mem-1", ENTITY, CBR, TENANT, "case-1",
                                "problem", Map.of(CbrAttributeKeys.CBR_TYPE, "unknown",
                              MemoryAttributeKeys.SOLUTION, "sol"), Instant.now(), null, null, null, null);
        assertThat(CbrMemoryDeserializer.deserialize(memory)).isEmpty();
    }

    @Test
    void deserialize_missingCbrType_returnsEmpty() {
        var memory = new Memory("mem-1", ENTITY, CBR, TENANT, "case-1",
                                "problem", Map.of(MemoryAttributeKeys.SOLUTION, "sol"), Instant.now(), null, null, null, null);
        assertThat(CbrMemoryDeserializer.deserialize(memory)).isEmpty();
    }

    @Test
    void deserialize_missingSolution_returnsEmpty() {
        var memory = new Memory("mem-1", ENTITY, CBR, TENANT, "case-1",
                                "problem", Map.of(CbrAttributeKeys.CBR_TYPE, "textual"), Instant.now(), null, null, null, null);
        assertThat(CbrMemoryDeserializer.deserialize(memory)).isEmpty();
    }

    @Test
    void deserialize_malformedFeaturesJson_returnsEmpty() {
        var memory = new Memory("mem-1", ENTITY, CBR, TENANT, "case-1",
                                "problem", Map.of(CbrAttributeKeys.CBR_TYPE, CbrFeatureRecord.CBR_TYPE,
                                                  MemoryAttributeKeys.SOLUTION, "sol",
                                                  CbrAttributeKeys.CBR_FEATURES, "not valid json"),
                                Instant.now(), null, null, null, null);
        assertThat(CbrMemoryDeserializer.deserialize(memory)).isEmpty();
    }

    private java.util.Optional<CbrRecord> roundTrip(CbrRecord original, String caseType) {
        // Serialize using the same code path as QdrantCbrRecordStore
        MemoryInput input = serializeToMemoryInput(original, ENTITY, CBR, TENANT, "case-1", caseType);
        // Convert to Memory (simulating what CaseMemoryStore.store() → query() returns)
        Memory memory = new Memory("mem-1", input.entityId(), input.domain(), input.tenantId(),
                                   input.caseId(), input.text(), input.attributes(), Instant.now(), null, null, null, null);
        return CbrMemoryDeserializer.deserialize(memory);
    }

    /**
     * Package-private access to serialization — calls the same static method
     * that QdrantCbrRecordStore uses internally.
     */
    private static MemoryInput serializeToMemoryInput(CbrRecord cbrRecord, String entityId,
                                                      MemoryDomain domain, String tenantId,
                                                      String caseId, String caseType) {
        // Replicates QdrantCbrRecordStore.serializeToMemoryInput() logic
        // After refactoring to CbrAttributeKeys, both paths use the same constants
        return CbrMemorySerializer.serialize(cbrRecord, entityId, domain, tenantId, caseId, caseType);
    }
}
