package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphType;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MindMapExtractorTest {

    private static final String TENANT = "test-tenant";
    private InMemoryMindMapStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
    }

    @Test
    void extractEntitiesFromConversation() {
        String response = """
            {"entities": [
                {"name": "Alice", "type": "PERSON", "properties": {"role": "engineer"}, "confidence": "STATED"},
                {"name": "Acme Corp", "type": "ORGANISATION", "properties": {}, "confidence": "STATED"}
            ], "relationships": [
                {"source": "Alice", "target": "Acme Corp", "type": "works-at", "confidence": "STATED"}
            ], "contradictions": []}
            """;
        var extractor = createExtractor(response);

        ExtractionResult result = extractor.extract("Alice is an engineer at Acme Corp", TENANT);

        assertThat(result.entities()).hasSize(2);
        assertThat(result.entities()).extracting(ExtractedEntity::name)
            .containsExactlyInAnyOrder("Alice", "Acme Corp");
        assertThat(result.entities()).allMatch(ExtractedEntity::created);
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.relationships().get(0).edgeType()).isEqualTo("works-at");
        assertThat(result.entityNames()).containsExactlyInAnyOrder("Alice", "Acme Corp");
    }

    @Test
    void resolveExistingEntity() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphType.PERSON, null), TENANT);
        store.addNode(new NodeInput("Alice", sgId, ConfidenceOrigin.STATED, null, "test",
            null, null, null, null, null, null, null, Map.of("role", "manager")), TENANT);

        String response = """
            {"entities": [
                {"name": "Alice", "type": "PERSON", "properties": {"email": "alice@acme.com"}, "confidence": "STATED"}
            ], "relationships": [], "contradictions": []}
            """;
        var extractor = createExtractor(response);

        ExtractionResult result = extractor.extract("Alice's email is alice@acme.com", TENANT);

        assertThat(result.entities()).hasSize(1);
        assertThat(result.entities().get(0).created()).isFalse();

        MindMapNode alice = store.resolveNode("Alice", null, TENANT);
        assertThat(alice.property("email")).hasValue("alice@acme.com");
        assertThat(alice.property("role")).hasValue("manager");
    }

    @Test
    void detectContradiction() {
        String sgPerson = store.createSubgraph(new SubgraphInput("People", SubgraphType.PERSON, null), TENANT);
        String aliceId = store.addNode(new NodeInput("Alice", sgPerson, ConfidenceOrigin.STATED, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        String sgOrg = store.createSubgraph(new SubgraphInput("Orgs", SubgraphType.ORGANISATION, null), TENANT);
        String acmeId = store.addNode(new NodeInput("Acme", sgOrg, ConfidenceOrigin.STATED, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        store.addEdge(new EdgeInput(aliceId, acmeId, "works-at", ConfidenceOrigin.STATED, null, "test",
            null, null, null, null, null, Map.of()), TENANT);

        String response = """
            {"entities": [
                {"name": "Alice", "type": "PERSON", "properties": {}, "confidence": "STATED"},
                {"name": "Initech", "type": "ORGANISATION", "properties": {}, "confidence": "STATED"}
            ], "relationships": [
                {"source": "Alice", "target": "Initech", "type": "works-at", "confidence": "STATED"}
            ], "contradictions": [
                {"entity": "Alice", "property": "works-at", "existing": "Acme", "extracted": "Initech", "explanation": "Alice changed jobs"}
            ]}
            """;
        var extractor = createExtractor(response);

        ExtractionResult result = extractor.extract("Alice now works at Initech", TENANT);

        assertThat(result.contradictions()).hasSize(1);
        assertThat(result.contradictions().get(0).entityName()).isEqualTo("Alice");
        assertThat(result.contradictions().get(0).existingValue()).isEqualTo("Acme");
    }

    @Test
    void carryForwardIncludedInPrompt() {
        String response = """
            {"entities": [
                {"name": "Alice", "type": "PERSON", "properties": {"mood": "excited"}, "confidence": "INFERRED"}
            ], "relationships": [], "contradictions": []}
            """;
        var agent = new TestAgentProvider(response);
        var extractor = new MindMapExtractor(store, stubInstance(agent));

        extractor.extract("She seemed really excited", TENANT, List.of("Alice", "Acme"));

        assertThat(agent.lastUserPrompt).contains("Alice");
        assertThat(agent.lastUserPrompt).contains("Acme");
    }

    @Test
    void emptyLlmResponseReturnsEmpty() {
        var extractor = createExtractor("");

        ExtractionResult result = extractor.extract("Hello world", TENANT);

        assertThat(result).isSameAs(ExtractionResult.EMPTY);
    }

    @Test
    void noOpAgentProviderReturnsEmpty() {
        var noOp = new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().empty();
            }
            @Override public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
        var extractor = new MindMapExtractor(store, stubInstance(noOp));

        ExtractionResult result = extractor.extract("Hello world", TENANT);

        assertThat(result).isSameAs(ExtractionResult.EMPTY);
    }

    @Test
    void malformedJsonReturnsEmpty() {
        var extractor = createExtractor("This is not JSON at all");

        ExtractionResult result = extractor.extract("Hello world", TENANT);

        assertThat(result).isSameAs(ExtractionResult.EMPTY);
    }

    @Test
    void unknownEntityTypeDefaultsToGeneral() {
        String response = """
            {"entities": [
                {"name": "Climate Change", "type": "TOPIC", "properties": {}, "confidence": "STATED"}
            ], "relationships": [], "contradictions": []}
            """;
        var extractor = createExtractor(response);

        ExtractionResult result = extractor.extract("We discussed climate change", TENANT);

        assertThat(result.entities()).hasSize(1);
        assertThat(result.entities().get(0).subgraphType()).isEqualTo("GENERAL");
    }

    @Test
    void subgraphCacheIsTenantAware() {
        String response = """
            {"entities": [{"name": "Alice", "type": "PERSON", "properties": {}, "confidence": "STATED"}], "relationships": [], "contradictions": []}
            """;
        var extractor = createExtractor(response);

        extractor.extract("Alice is here", "tenant-a");
        extractor.extract("Alice is here", "tenant-b");

        List<MindMapSubgraph> sgA = store.listSubgraphs("tenant-a");
        List<MindMapSubgraph> sgB = store.listSubgraphs("tenant-b");
        assertThat(sgA).hasSize(1);
        assertThat(sgB).hasSize(1);
        assertThat(sgA.get(0).id()).isNotEqualTo(sgB.get(0).id());
    }

    @Test
    void relationshipWithUnresolvedEndpointIsSkipped() {
        String response = """
            {"entities": [
                {"name": "Alice", "type": "PERSON", "properties": {}, "confidence": "STATED"}
            ], "relationships": [
                {"source": "Alice", "target": "UnknownEntity", "type": "knows", "confidence": "INFERRED"}
            ], "contradictions": []}
            """;
        var extractor = createExtractor(response);

        ExtractionResult result = extractor.extract("Alice knows someone", TENANT);

        assertThat(result.entities()).hasSize(1);
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void contextSerializationIncludesExistingNodes() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphType.PERSON, null), TENANT);
        String aliceId = store.addNode(new NodeInput("Alice", sgId, ConfidenceOrigin.STATED, null, "test",
            null, null, null, null, null, null, null, Map.of("role", "engineer")), TENANT);
        String sgOrg = store.createSubgraph(new SubgraphInput("Orgs", SubgraphType.ORGANISATION, null), TENANT);
        String acmeId = store.addNode(new NodeInput("Acme", sgOrg, ConfidenceOrigin.STATED, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        store.addEdge(new EdgeInput(aliceId, acmeId, "works-at", ConfidenceOrigin.STATED, null, "test",
            null, null, null, null, null, Map.of()), TENANT);

        String response = """
            {"entities": [], "relationships": [], "contradictions": []}
            """;
        var agent = new TestAgentProvider(response);
        var extractor = new MindMapExtractor(store, stubInstance(agent));

        extractor.extract("Alice mentioned her project", TENANT, List.of("Alice"));

        assertThat(agent.lastUserPrompt).contains("Alice");
        assertThat(agent.lastUserPrompt).contains("works-at");
    }

    @Test
    void nullConversationTextReturnsEmpty() {
        var extractor = createExtractor("anything");
        assertThat(extractor.extract(null, TENANT)).isSameAs(ExtractionResult.EMPTY);
        assertThat(extractor.extract("  ", TENANT)).isSameAs(ExtractionResult.EMPTY);
    }

    @Test
    void twoArgumentFormDelegatesToThreeArgument() {
        String response = """
            {"entities": [{"name": "Bob", "type": "PERSON", "properties": {}, "confidence": "STATED"}], "relationships": [], "contradictions": []}
            """;
        var extractor = createExtractor(response);

        ExtractionResult result = extractor.extract("Bob is here", TENANT);

        assertThat(result.entities()).hasSize(1);
        assertThat(result.entities().get(0).name()).isEqualTo("Bob");
    }

    // --- Test helpers ---

    private MindMapExtractor createExtractor(String cannedResponse) {
        return new MindMapExtractor(store, stubInstance(new TestAgentProvider(cannedResponse)));
    }

    static Instance<AgentProvider> stubInstance(AgentProvider provider) {
        return new Instance<>() {
            @Override public AgentProvider get() { return provider; }
            @Override public boolean isUnsatisfied() { return false; }
            @Override public boolean isResolvable() { return true; }
            @Override public boolean isAmbiguous() { return false; }
            @Override public void destroy(AgentProvider instance) {}
            @Override public Handle<AgentProvider> getHandle() { return null; }
            @Override public Iterable<Handle<AgentProvider>> handles() { return null; }
            @Override public Instance<AgentProvider> select(java.lang.annotation.Annotation... qualifiers) { return this; }
            @Override public <U extends AgentProvider> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { return null; }
            @Override public <U extends AgentProvider> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { return null; }
            @Override public Iterator<AgentProvider> iterator() { return List.of(provider).iterator(); }
        };
    }

    static class TestAgentProvider implements AgentProvider {
        private final String response;
        int invocationCount = 0;
        String lastUserPrompt;

        TestAgentProvider(String response) { this.response = response; }

        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            invocationCount++;
            lastUserPrompt = config.userPrompt();
            if (response == null || response.isEmpty()) {
                return Multi.createFrom().empty();
            }
            return Multi.createFrom().items(
                new AgentEvent.TextDelta(response),
                new AgentEvent.InvocationComplete(100, 50, 0, 0, 0, 0.001, 500L, 400L, "test", 1, false));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }
}
