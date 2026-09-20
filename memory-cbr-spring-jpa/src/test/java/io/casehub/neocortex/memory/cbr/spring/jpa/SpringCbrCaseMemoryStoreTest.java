package io.casehub.neocortex.memory.cbr.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CaseTypeScope;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.jpa.CbrCaseEntity;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringCbrCaseMemoryStoreTest.TestConfig.class)
class SpringCbrCaseMemoryStoreTest {

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = CbrCaseEntityRepository.class)
    @EntityScan(basePackageClasses = CbrCaseEntity.class)
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SpringCbrCaseMemoryStore springCbrCaseMemoryStore(
                CbrCaseEntityRepository repo, ObjectMapper mapper) {
            return new SpringCbrCaseMemoryStore(repo, mapper);
        }
    }

    @Autowired CbrCaseMemoryStore store;

    @BeforeEach
    void setup() {
    }

    @Test
    void storeAndRetrieve() {
        var cbrCase = new FeatureVectorCbrCase(
                "slow response", "add cache", null, null,
                Map.of("severity", new FeatureValue.StringVal("high")), null, null);

        String id = store.store(cbrCase, "incident", "e1",
                new MemoryDomain("ops"), "t1", "case-1", Path.root());
        assertNotNull(id);

        var results = store.retrieveSimilar(
                CbrQuery.of("t1", new MemoryDomain("ops"), Path.root(), "incident",
                        Map.of("severity", new FeatureValue.StringVal("high")), 10)
                        .withMinSimilarity(0.0),
                CbrCase.class);

        assertEquals(1, results.size());
        assertEquals("slow response", results.getFirst().cbrCase().problem());
    }

    @Test
    void eraseByEntityAndDomain() {
        var cbrCase = new FeatureVectorCbrCase(
                "problem", "solution", null, null, Map.of(), null, null);
        store.store(cbrCase, "test-type", "e1", new MemoryDomain("d"), "t1", "c1", Path.root());

        int erased = store.erase(new EraseRequest(
                Subject.of("unknown", "e1"), new MemoryDomain("d"), "t1", null));
        assertEquals(1, erased);
    }

    @Test
    void supersedeAndReinstate() {
        var cbrCase = new FeatureVectorCbrCase(
                "problem", "solution", null, null, Map.of(), null, null);
        store.store(cbrCase, "test-type", "e1", new MemoryDomain("d"), "t1", "c1", Path.root());

        assertTrue(store.supersede("c1", "t1", null, "outdated"));
        var status = store.getSupersessionStatus("c1", "t1");
        assertTrue(status.superseded());

        assertTrue(store.reinstate("c1", "t1"));
        var reinstated = store.getSupersessionStatus("c1", "t1");
        assertFalse(reinstated.superseded());
    }

    @Test
    void eraseEntity() {
        var cbrCase = new FeatureVectorCbrCase(
                "problem", "solution", null, null, Map.of(), null, null);
        store.store(cbrCase, "test-type", "e1", new MemoryDomain("d"), "t1", "c1", Path.root());
        store.store(cbrCase, "test-type", "e1", new MemoryDomain("d"), "t1", "c2", Path.root());

        int erased = store.eraseEntity("e1", "t1");
        assertEquals(2, erased);
    }

    @Test
    void supersededCasesNotRetrieved() {
        var cbrCase = new FeatureVectorCbrCase(
                "problem", "solution", null, null,
                Map.of("severity", new FeatureValue.StringVal("high")), null, null);
        store.store(cbrCase, "incident", "e1", new MemoryDomain("ops"), "t1", "c1", Path.root());
        store.supersede("c1", "t1", null, "outdated");

        var results = store.retrieveSimilar(
                CbrQuery.of("t1", new MemoryDomain("ops"), Path.root(), "incident",
                        Map.of("severity", new FeatureValue.StringVal("high")), 10)
                        .withMinSimilarity(0.0),
                CbrCase.class);

        assertTrue(results.isEmpty());
    }
}
