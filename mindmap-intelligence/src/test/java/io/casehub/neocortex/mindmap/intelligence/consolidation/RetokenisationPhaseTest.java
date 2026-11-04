package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.entity.token.SurfaceForm;
import io.casehub.neocortex.entity.token.SurfaceFormTrie;
import io.casehub.neocortex.entity.token.TokenFormat;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetokenisationPhaseTest {

    private static final String TENANT = "tenant-1";
    private static final MemoryDomain DOMAIN = new MemoryDomain("experience");

    private InMemoryMemoryStore memoryStore;
    private InMemoryMindMapStore mindMapStore;
    private SurfaceFormTrie trie;
    private RetokenisationPhase phase;

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    @BeforeEach
    void setUp() {
        memoryStore = new InMemoryMemoryStore(principal);
        mindMapStore = new InMemoryMindMapStore();
        trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Alice", "full", "person-1"),
            new SurfaceForm("Bob", "full", "person-2")));
        phase = new RetokenisationPhase(memoryStore, mindMapStore, t -> trie, 50);
    }

    @Test
    void retokenises_literal_entity_names() {
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "a1"), DOMAIN, TENANT, "Alice met Bob"));
        phase.run(TENANT, List.of());
        var memories = memoryStore.scan(new MemoryScanRequest(TENANT, null, null, null, 100, null));
        assertEquals(1, memories.size());
        assertTrue(memories.get(0).text().contains("{{person-1:full:Alice}}"));
        assertTrue(memories.get(0).text().contains("{{person-2:full:Bob}}"));
    }

    @Test
    void skips_text_that_has_no_entity_matches() {
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "a1"), DOMAIN, TENANT, "the weather was nice"));
        phase.run(TENANT, List.of());
        var memories = memoryStore.scan(new MemoryScanRequest(TENANT, null, null, null, 100, null));
        assertEquals("the weather was nice", memories.get(0).text());
    }

    @Test
    void idempotent_on_already_tokenised_text() {
        String tokenised = "{{person-1:full:Alice}} met {{person-2:full:Bob}}";
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "a1"), DOMAIN, TENANT, tokenised));
        phase.run(TENANT, List.of());
        var memories = memoryStore.scan(new MemoryScanRequest(TENANT, null, null, null, 100, null));
        assertEquals(tokenised, memories.get(0).text());
    }

    @Test
    void persists_cursor_and_resumes() {
        for (int i = 0; i < 5; i++) {
            memoryStore.store(MemoryInput.of(
                Subject.of("agent", "a1"), DOMAIN, TENANT, "Alice number " + i));
        }
        var smallPhase = new RetokenisationPhase(memoryStore, mindMapStore, t -> trie, 2);
        smallPhase.run(TENANT, List.of());

        var all = memoryStore.scan(new MemoryScanRequest(TENANT, null, null, null, 100, null));
        long tokenised = all.stream()
            .filter(m -> !TokenFormat.extractEntityIds(m.text()).isEmpty())
            .count();
        assertEquals(2, tokenised);

        smallPhase.run(TENANT, List.of());
        all = memoryStore.scan(new MemoryScanRequest(TENANT, null, null, null, 100, null));
        tokenised = all.stream()
            .filter(m -> !TokenFormat.extractEntityIds(m.text()).isEmpty())
            .count();
        assertEquals(4, tokenised);
    }

    @Test
    void no_op_when_trie_provider_returns_null() {
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "a1"), DOMAIN, TENANT, "Alice is here"));
        var nullTriePhase = new RetokenisationPhase(memoryStore, mindMapStore, t -> null, 50);
        nullTriePhase.run(TENANT, List.of());
        var memories = memoryStore.scan(new MemoryScanRequest(TENANT, null, null, null, 100, null));
        assertEquals("Alice is here", memories.get(0).text());
    }
}
