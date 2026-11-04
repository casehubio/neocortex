package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenisingEnrichmentStepTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore store;
    private SurfaceFormTrieManager trieManager;
    private TokenisingEnrichmentStep step;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        var cache = new EntityIdentityCache(store);
        trieManager = new SurfaceFormTrieManager(cache);
        step = new TokenisingEnrichmentStep(trieManager, true);

        String sgId = store.createSubgraph(
            new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        store.addNode(
            NodeInput.of("Jane Doe", sgId)
                .withProperties(Map.of("canonicalFirst", "Jane", "canonicalLast", "Doe")),
            TENANT);
    }

    @AfterEach
    void tearDown() {
        trieManager.shutdown();
    }

    @Test
    void appliesToNonBlankText() {
        var input = MemoryInput.of(Subject.of("person", "p1"),
            new MemoryDomain("experience"), TENANT, "some text");
        assertThat(step.appliesTo(input)).isTrue();
    }

    @Test
    void tokenisesKnownEntities() {
        var input = MemoryInput.of(Subject.of("person", "p1"),
            new MemoryDomain("experience"), TENANT, "Jane Doe walked home");
        var result = step.enrich(input);
        assertThat(result.text()).contains("{{");
        assertThat(result.text()).contains(":full:Jane Doe}}");
    }

    @Test
    void leavesUnknownTextUnchanged() {
        var input = MemoryInput.of(Subject.of("person", "p1"),
            new MemoryDomain("experience"), TENANT, "Diego walked home");
        var result = step.enrich(input);
        assertThat(result.text()).isEqualTo("Diego walked home");
    }

    @Test
    void preservesOtherMemoryInputFields() {
        var input = MemoryInput.of(Subject.of("person", "p1"),
            new MemoryDomain("experience"), TENANT, "Jane walked")
            .withCaseId("case-1")
            .withAttribute("key", "value");
        var result = step.enrich(input);
        assertThat(result.caseId()).isEqualTo("case-1");
        assertThat(result.attributes()).containsEntry("key", "value");
        assertThat(result.tenantId()).isEqualTo(TENANT);
    }

    @Test
    void priorityIs100() {
        assertThat(step.priority()).isEqualTo(100);
    }
}
