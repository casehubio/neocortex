package io.casehub.neocortex.memory.inmem;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.memory.testing.EnrichAttributesContractTest;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;

import java.util.Set;

class InMemoryEnrichAttributesTest extends EnrichAttributesContractTest {

    private static final String TENANT = "test-tenant";

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId()             { return "actor"; }
        @Override public Set<String> groups()         { return Set.of(); }
        @Override public String tenancyId()           { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private InMemoryMemoryStore store;

    @BeforeEach
    void setUp() { store = new InMemoryMemoryStore(principal); }

    @Override protected CaseMemoryStore store() { return store; }
    @Override protected String tenantId() { return TENANT; }
}
