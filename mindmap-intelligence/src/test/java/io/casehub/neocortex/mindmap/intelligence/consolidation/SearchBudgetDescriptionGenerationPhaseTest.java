package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.SearchBudgetConfig;
import io.casehub.neocortex.cognitive.SearchFatigueEffects;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.agent.session.SessionPipeline;
import jakarta.annotation.Priority;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchBudgetDescriptionGenerationPhaseTest {

    @Test
    void priority_is_28() {
        var annotation = SearchBudgetDescriptionGenerationPhase.class.getAnnotation(Priority.class);
        assertNotNull(annotation);
        assertEquals(28, annotation.value());
    }

    private static InMemoryMindMapStore newStore() {
        return new InMemoryMindMapStore();
    }

    @Test
    void creates_node_with_search_budget_trait() {
        var store = newStore();
        var defaults = CognitiveDefaults.empty("agent1")
                .withTenantId("tenant1")
                .withSearchBudgetConfig(SearchBudgetConfig.defaults());
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var phase = new SearchBudgetDescriptionGenerationPhase(store, registry, (SessionPipeline) null);
        phase.run("tenant1", List.of());

        var nodes = store.search(MindMapQuery.of("tenant1", 100));
        var generated = nodes.stream()
                .filter(n -> n.traits().contains(SearchBudgetDescriptionGenerationPhase.GENERATED_TRAIT))
                .toList();
        assertFalse(generated.isEmpty(), "Should create SearchBudgetGenerated node");
        assertTrue(generated.get(0).property("agent-id").isPresent());
        assertEquals("agent1", generated.get(0).property("agent-id").get());
    }

    @Test
    void node_has_description_property() {
        var store = newStore();
        var defaults = CognitiveDefaults.empty("agent1")
                .withTenantId("tenant1")
                .withSearchBudgetConfig(SearchBudgetConfig.defaults());
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var phase = new SearchBudgetDescriptionGenerationPhase(store, registry, (SessionPipeline) null);
        phase.run("tenant1", List.of());

        var nodes = store.search(MindMapQuery.of("tenant1", 100));
        var generated = nodes.stream()
                .filter(n -> n.traits().contains(SearchBudgetDescriptionGenerationPhase.GENERATED_TRAIT))
                .findFirst()
                .orElseThrow();
        assertTrue(generated.property("description").isPresent(),
                "Node should have description property");
        assertFalse(generated.property("description").get().isBlank(),
                "Description should not be blank");
    }

    @Test
    void idempotent_updates_existing_node() {
        var store = newStore();
        var defaults = CognitiveDefaults.empty("agent1")
                .withTenantId("tenant1")
                .withSearchBudgetConfig(SearchBudgetConfig.defaults());
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var phase = new SearchBudgetDescriptionGenerationPhase(store, registry, (SessionPipeline) null);
        phase.run("tenant1", List.of());
        phase.run("tenant1", List.of());

        var nodes = store.search(MindMapQuery.of("tenant1", 100));
        var generated = nodes.stream()
                .filter(n -> n.traits().contains(SearchBudgetDescriptionGenerationPhase.GENERATED_TRAIT))
                .filter(n -> "agent1".equals(n.property("agent-id").orElse(null)))
                .toList();
        assertEquals(1, generated.size(), "Should not duplicate — idempotent update");
    }

    @Test
    void skips_agent_without_config() {
        var store = newStore();
        var defaults = CognitiveDefaults.empty("agent1").withTenantId("tenant1");
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var phase = new SearchBudgetDescriptionGenerationPhase(store, registry, (SessionPipeline) null);
        phase.run("tenant1", List.of());

        var nodes = store.search(MindMapQuery.of("tenant1", 100));
        var generated = nodes.stream()
                .filter(n -> n.traits().contains(SearchBudgetDescriptionGenerationPhase.GENERATED_TRAIT))
                .toList();
        assertTrue(generated.isEmpty(), "Should skip agent without searchBudgetConfig");
    }

    @Test
    void fallback_generates_coherent_prose() {
        var config = SearchBudgetConfig.defaults();
        String fallback = SearchBudgetDescriptionGenerationPhase.generateFallback(config);
        assertNotNull(fallback);
        assertFalse(fallback.isBlank());
        assertFalse(fallback.contains("breadth"), "Fallback should not name parameters");
        assertFalse(fallback.contains("laterality"), "Fallback should not name parameters");
    }

    @Test
    void fallback_includes_channel_tendencies_for_high_specific_depth() {
        var channelMod = new io.casehub.neocortex.cognitive.ChannelModulationConfig(
                0.18, 0.12, 0.08, 0.06, 0.04, 0.08, 0.06, 0.14);
        var    config   = new SearchBudgetConfig(6.0, 0.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, 1.4, channelMod, 0.85, null, null);
        String fallback = SearchBudgetDescriptionGenerationPhase.generateFallback(config);
        assertTrue(fallback.contains("narrows") || fallback.contains("depth"),
                   "High specificDepthBoost should produce depth-related description: " + fallback);
    }

    @Test
    void fallback_omits_channel_tendencies_when_null() {
        var    config   = SearchBudgetConfig.defaults();
        String fallback = SearchBudgetDescriptionGenerationPhase.generateFallback(config);
        assertNotNull(fallback);
        assertFalse(fallback.isEmpty());
        assertFalse(fallback.contains("gaps"), "Null channel modulation should not mention gap pressure");
    }

    @Test
    void extreme_config_generates_extreme_description_property() {
        var store = newStore();
        var config = SearchBudgetConfig.defaults()
                                       .withExtremeInteraction(io.casehub.neocortex.cognitive.ExtremeInteractionConfig.defaults())
                                       .withFatigueEffects(SearchFatigueEffects.defaults());
        var defaults = CognitiveDefaults.empty("agent1")
                                        .withTenantId("tenant1")
                                        .withSearchBudgetConfig(config);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var phase = new SearchBudgetDescriptionGenerationPhase(store, registry, (io.casehub.platform.agent.session.SessionPipeline) null);
        phase.run("tenant1", List.of());

        var nodes = store.search(MindMapQuery.of("tenant1", 100));
        var generated = nodes.stream()
                             .filter(n -> n.traits().contains(SearchBudgetDescriptionGenerationPhase.GENERATED_TRAIT))
                             .findFirst()
                             .orElseThrow();
        assertTrue(generated.property("description").isPresent(), "Should have normal description");
        assertTrue(generated.property("extreme-description").isPresent(), "Should have extreme description");
        assertFalse(generated.property("extreme-description").get().isBlank());
    }

    @Test
    void no_extreme_config_omits_extreme_description() {
        var store = newStore();
        var defaults = CognitiveDefaults.empty("agent1")
                                        .withTenantId("tenant1")
                                        .withSearchBudgetConfig(SearchBudgetConfig.defaults());
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var phase = new SearchBudgetDescriptionGenerationPhase(store, registry, (io.casehub.platform.agent.session.SessionPipeline) null);
        phase.run("tenant1", List.of());

        var nodes = store.search(MindMapQuery.of("tenant1", 100));
        var generated = nodes.stream()
                             .filter(n -> n.traits().contains(SearchBudgetDescriptionGenerationPhase.GENERATED_TRAIT))
                             .findFirst()
                             .orElseThrow();
        assertTrue(generated.property("description").isPresent());
        assertFalse(generated.property("extreme-description").isPresent(),
                    "Default config should not generate extreme-description");
    }

    @Test
    void extreme_fallback_generates_coherent_prose() {
        var config = SearchBudgetConfig.defaults()
                                       .withFatigueEffects(SearchFatigueEffects.defaults());
        String fallback = SearchBudgetDescriptionGenerationPhase.generateExtremeFallback(config);
        assertNotNull(fallback);
        assertFalse(fallback.isBlank());
    }


}
