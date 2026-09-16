package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryReloadTest {

    @Test
    void reload_swapsProfilesAtomically() {
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("alice"));

        assertThat(registry.forAgent("alice")).isPresent();
        assertThat(registry.forAgent("bob")).isEmpty();

        registry.reload(Map.of(
            "bob", CognitiveDefaults.empty("bob")));

        assertThat(registry.forAgent("alice")).isEmpty();
        assertThat(registry.forAgent("bob")).isPresent();
    }

    @Test
    void reloadGlobalRules_swapsAtomically() {
        var traitRule = new io.casehub.neocortex.mindmap.DeclarativeTraitRule(
                "TestTrait",
                new io.casehub.neocortex.mindmap.RuleCondition.HasEdgeTypes(java.util.Set.of("knows")));
        var registry = DeclarativeRuleRegistry.of(java.util.List.of(), java.util.List.of());

        assertThat(registry.traitRules(null)).isEmpty();

        registry.reloadGlobalRules(
                java.util.List.of(traitRule),
                java.util.List.of());

        assertThat(registry.traitRules(null)).hasSize(1);
        assertThat(registry.traitRules(null).get(0).traitName()).isEqualTo("TestTrait");
    }

    @Test
    void reloadGlobalRules_traitAndDerivedAreConsistent() {
        var registry = DeclarativeRuleRegistry.of(java.util.List.of(), java.util.List.of());

        var traitRule = new io.casehub.neocortex.mindmap.DeclarativeTraitRule(
                "NewTrait",
                new io.casehub.neocortex.mindmap.RuleCondition.HasEdgeTypes(java.util.Set.of("knows")));
        var edgeRule = new io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule(
                "new-edge", java.util.Set.of("knows"), null, java.util.List.of());

        registry.reloadGlobalRules(
                java.util.List.of(traitRule),
                java.util.List.of(edgeRule));

        assertThat(registry.traitRules(null)).hasSize(1);
        assertThat(registry.derivedEdgeRules(null)).hasSize(1);
    }


}
