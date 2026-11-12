package io.casehub.neocortex.memory.cbr.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.RetrievalMode;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.jpa.CbrRecordEntity;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Map;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringCbrRecordStoreTest.TestConfig.class)
class SpringCbrRecordStoreTest {

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = CbrRecordEntityRepository.class)
    @EntityScan(basePackageClasses = CbrRecordEntity.class)
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SpringCbrRecordStore springCbrRecordStore(
                CbrRecordEntityRepository repo, ObjectMapper mapper) {
            return new SpringCbrRecordStore(repo, mapper);
        }
    }

    @Autowired
    CbrRecordStore store;

    @BeforeEach
    void setup() {
    }

    @ParameterizedTest
    @CsvSource(value = {"0.0,0.5", "0.8,0.9", "1.0,1.0", "null,0.675"}, nullValues = "null")
    void outcomeLearningRateOverrideTakesPrecedenceOverSchema(Double learningRate, double expectedConfidence) {
        store.registerSchema(new CbrRecordSchema("diagnosis", List.of(FeatureField.categorical("severity")), 0.35));
        Map<String, FeatureValue> features = Map.of("severity", FeatureValue.string("HIGH"));
        var domain = new MemoryDomain("clinical");
        store.store(new CbrFeatureRecord("problem", "solution", null, Confidence.unknown(0.5), features, null, null),
                "diagnosis", "patient", domain, "t1", "case-outcome", Path.root());

        store.recordOutcome("case-outcome", CbrOutcome.of(1.0, "success", Instant.now()), "t1", learningRate);

        var result = store.retrieveSimilar(CbrQuery.of("t1", domain, Path.root(), "diagnosis", features, 1)
                .withRetrievalMode(RetrievalMode.FEATURE_ONLY), CbrFeatureRecord.class);
        assertEquals(1, result.size());
        assertEquals(expectedConfidence, result.getFirst().cbrRecord().confidence().value(), 0.000001);
    }

    @Test
    void storeAndRetrieve() {
        var cbrRecord = new CbrFeatureRecord(
                "slow response", "add cache", null, null,
                Map.of("severity", new FeatureValue.StringVal("high")), null, null);

        String id = store.store(cbrRecord, "incident", "e1",
                new MemoryDomain("ops"), "t1", "case-1", Path.root());
        assertNotNull(id);

        var results = store.retrieveSimilar(
                CbrQuery.of("t1", new MemoryDomain("ops"), Path.root(), "incident",
                        Map.of("severity", new FeatureValue.StringVal("high")), 10)
                        .withMinSimilarity(0.0),
                CbrRecord.class);

        assertEquals(1, results.size());
        assertEquals("slow response", results.getFirst().cbrRecord().problem());
    }

    @Test
    void eraseByEntityAndDomain() {
        var cbrRecord = new CbrFeatureRecord(
                "problem", "solution", null, null, Map.of(), null, null);
        store.store(cbrRecord, "test-type", "e1", new MemoryDomain("d"), "t1", "c1", Path.root());

        int erased = store.erase(new EraseRequest(
                Subject.of("unknown", "e1"), new MemoryDomain("d"), "t1", null));
        assertEquals(1, erased);
    }

    @Test
    void supersedeAndReinstate() {
        var cbrRecord = new CbrFeatureRecord(
                "problem", "solution", null, null, Map.of(), null, null);
        store.store(cbrRecord, "test-type", "e1", new MemoryDomain("d"), "t1", "c1", Path.root());

        assertTrue(store.supersede("c1", null, "outdated", "t1"));
        var status = store.getSupersessionStatus("c1", "t1");
        assertTrue(status.superseded());

        assertTrue(store.reinstate("c1", "t1"));
        var reinstated = store.getSupersessionStatus("c1", "t1");
        assertFalse(reinstated.superseded());
    }

    @Test
    void eraseEntity() {
        var cbrRecord = new CbrFeatureRecord(
                "problem", "solution", null, null, Map.of(), null, null);
        store.store(cbrRecord, "test-type", "e1", new MemoryDomain("d"), "t1", "c1", Path.root());
        store.store(cbrRecord, "test-type", "e1", new MemoryDomain("d"), "t1", "c2", Path.root());

        int erased = store.eraseEntity("e1", "t1");
        assertEquals(2, erased);
    }

    @Test
    void supersededCasesNotRetrieved() {
        var cbrRecord = new CbrFeatureRecord(
                "problem", "solution", null, null,
                Map.of("severity", new FeatureValue.StringVal("high")), null, null);
        store.store(cbrRecord, "incident", "e1", new MemoryDomain("ops"), "t1", "c1", Path.root());
        store.supersede("c1", null, "outdated", "t1");

        var results = store.retrieveSimilar(
                CbrQuery.of("t1", new MemoryDomain("ops"), Path.root(), "incident",
                        Map.of("severity", new FeatureValue.StringVal("high")), 10)
                        .withMinSimilarity(0.0),
                CbrRecord.class);

        assertTrue(results.isEmpty());
    }
}
