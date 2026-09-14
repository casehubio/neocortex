package io.casehub.neocortex.memory.inmem;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.testing.CaseMemoryStoreContractTest;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMemoryStoreTest extends CaseMemoryStoreContractTest {

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId()             { return "actor"; }
        @Override public Set<String> groups()         { return Set.of(); }
        @Override public String tenancyId()           { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private InMemoryMemoryStore sut;

    @BeforeEach
    void setUp() {
        sut = new InMemoryMemoryStore(principal);
    }

    @Override
    protected CaseMemoryStore store() {
        return sut;
    }

    // In-mem specific: question + CHRONOLOGICAL does substring filter (adapter behaviour, not contract)
    @Test
    void query_with_question_filters_by_text_containment() {
        sut.store(input("the cat sat on the mat"));
        sut.store(input("the dog barked loudly"));
        var results = sut.query(query().withQuestion("cat"));
        assertEquals(1, results.size());
        assertEquals("the cat sat on the mat", results.get(0).text());
    }

    @Test
    void relevance_order_accepted_without_error() {
        sut.store(input("some text"));
        assertDoesNotThrow(() ->
            sut.query(query().withOrder(MemoryOrder.RELEVANCE).withQuestion("some")));
    }

    @Test
    void eraseEntityAcrossTenants_removes_entity_from_admin_store() {
        // Admin store shares no backing map with sut — tests security gate + return value
        // using a store where the principal IS the admin.
        var adminPrincipal = new CurrentPrincipal() {
            @Override public String actorId()             { return "admin"; }
            @Override public Set<String> groups()         { return Set.of(); }
            @Override public String tenancyId()           { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return true; }
        };
        var adminStore = new InMemoryMemoryStore(adminPrincipal);
        adminStore.store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "data", Map.of(), null, null, null, null));
        int count = adminStore.eraseEntityAcrossTenants("entity-1", Set.of(TENANT));
        assertEquals(1, count);
        assertTrue(adminStore.query(MemoryQuery.forEntity("entity-1", DOMAIN, TENANT)).isEmpty());
    }

    @Test
    void eraseEntityAcrossTenants_requires_cross_tenant_admin() {
        // sut uses the default non-admin principal (isCrossTenantAdmin=false)
        assertThrows(SecurityException.class,
            () -> sut.eraseEntityAcrossTenants("entity-1", Set.of(TENANT)));
    }

    @Test
    void scanByDomain_returnsMatchingMemories() {
        sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("experience"),
                                  TENANT, null, "event one", Map.of(), null, null, null, null, null, null));
        sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("reflection"),
                                  TENANT, null, "reflect one", Map.of(), null, null, null, null, null, null));
        sut.store(new MemoryInput(Subject.of("agent", "a2"), new MemoryDomain("experience"),
                                  TENANT, null, "event two", Map.of(), null, null, null, null, null, null));

        var result = sut.scan(new MemoryScanRequest(TENANT, "experience", null, null, 100, null));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> m.domain().name().equals("experience")));
    }

    @Test
    void scanWithCursor_resumesFromAfterMemoryId() {
        String id1 = sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("experience"),
                                               TENANT, null, "event one", Map.of(), null, null, null, null, null, null));
        String id2 = sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("experience"),
                                               TENANT, null, "event two", Map.of(), null, null, null, null, null, null));
        String id3 = sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("experience"),
                                               TENANT, null, "event three", Map.of(), null, null, null, null, null, null));

        var page1 = sut.scan(new MemoryScanRequest(TENANT, "experience", null, null, 2, null));
        assertEquals(2, page1.size());

        var page2 = sut.scan(new MemoryScanRequest(TENANT, "experience", null, null, 2, page1.get(1).memoryId()));
        assertEquals(1, page2.size());
        assertEquals("event three", page2.get(0).text());
    }

    @Test
    void scanWithAttributeFilter_filtersCorrectly() {
        sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("experience"),
                                  TENANT, null, "obs", Map.of("event-type", "observation"), null, null, null, null, null, null));
        sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("experience"),
                                  TENANT, null, "act", Map.of("event-type", "action"), null, null, null, null, null, null));

        var result = sut.scan(new MemoryScanRequest(TENANT, "experience", "event-type", "observation", 100, null));
        assertEquals(1, result.size());
        assertEquals("obs", result.get(0).text());
    }

    @Test
    void scanNoDomain_returnsAllTenantsMemories() {
        sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("experience"),
                                  TENANT, null, "exp", Map.of(), null, null, null, null, null, null));
        sut.store(new MemoryInput(Subject.of("agent", "a1"), new MemoryDomain("reflection"),
                                  TENANT, null, "ref", Map.of(), null, null, null, null, null, null));

        var result = sut.scan(new MemoryScanRequest(TENANT, null, null, null, 100, null));
        assertEquals(2, result.size());
    }
}
