package io.casehub.neocortex.memory.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.jpa.MemoryEntry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.testing.spring.SpringFixedCurrentPrincipal;
import io.casehub.platform.testing.spring.SpringTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({SpringMemoryStoreTest.TestConfig.class, SpringTestConfig.class})
class SpringMemoryStoreTest {

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = MemoryEntryRepository.class)
    @EntityScan(basePackageClasses = MemoryEntry.class)
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SpringMemoryStore springMemoryStore(
                MemoryEntryRepository repo,
                SpringFixedCurrentPrincipal principal,
                ObjectMapper objectMapper) {
            return new SpringMemoryStore(repo, principal, objectMapper, false, "english");
        }
    }

    @Autowired CaseMemoryStore store;

    @Test
    void storeAndQueryChronological() {
        var input = MemoryInput.of(Subject.of("user", "u1"), new MemoryDomain("general"), TENANT, "hello world")
                .withAttributes(Map.of("key", "val"));

        String id = store.store(input);
        assertNotNull(id);

        var results = store.query(
                MemoryQuery.forSubject(Subject.of("user", "u1"), new MemoryDomain("general"), TENANT)
                        .withOrder(MemoryOrder.CHRONOLOGICAL)
                        .withLimit(10));

        assertEquals(1, results.size());
        assertEquals("hello world", results.getFirst().text());
        assertEquals(Map.of("key", "val"), results.getFirst().attributes());
    }

    @Test
    void eraseByDomainAndEntity() {
        var input = MemoryInput.of(Subject.of("user", "u1"), new MemoryDomain("general"), TENANT, "to be erased");
        store.store(input);

        int erased = store.erase(new EraseRequest(
                Subject.of("user", "u1"), new MemoryDomain("general"), TENANT, null));
        assertEquals(1, erased);

        var results = store.query(
                MemoryQuery.forSubject(Subject.of("user", "u1"), new MemoryDomain("general"), TENANT)
                        .withOrder(MemoryOrder.CHRONOLOGICAL)
                        .withLimit(10));
        assertTrue(results.isEmpty());
    }

    @Test
    void storeAllReturnsIds() {
        var inputs = List.of(
                MemoryInput.of(Subject.of("user", "u1"), new MemoryDomain("general"), TENANT, "one"),
                MemoryInput.of(Subject.of("user", "u1"), new MemoryDomain("general"), TENANT, "two")
        );
        var result = store.storeAll(inputs);
        assertEquals(2, result.stored().size());
        assertTrue(result.failures().isEmpty());
    }
}
