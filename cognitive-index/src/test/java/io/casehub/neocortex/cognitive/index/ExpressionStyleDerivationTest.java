package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionStyleDerivationTest {

    @Test
    void highAntagonismTriad_producesTheatricalTriumphAndSavoringGloat() {
        var facets = new AmpdFacets(Map.of(
                AmpdFacets.GRANDIOSITY, 85,
                AmpdFacets.CALLOUSNESS, 90,
                AmpdFacets.MANIPULATIVENESS, 80));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles).isNotNull();
        assertThat(styles.expressionFor("triumph")).contains("theatrical");
        assertThat(styles.expressionFor("gloating")).contains("savoring");
    }

    @Test
    void lowCallousness_producesGenuineGallantryAndSuppressedGloat() {
        var facets = new AmpdFacets(Map.of(
                AmpdFacets.CALLOUSNESS, 15,
                AmpdFacets.SUSPICIOUSNESS, 20,
                AmpdFacets.GRANDIOSITY, 25));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles).isNotNull();
        assertThat(styles.expressionFor("gallantry")).contains("genuine");
        assertThat(styles.expressionFor("gloating")).contains("uncomfortable");
    }

    @Test
    void highSuspiciousnessHighHostility_producesAggressiveDistrust() {
        var facets = new AmpdFacets(Map.of(
                AmpdFacets.SUSPICIOUSNESS, 80,
                AmpdFacets.HOSTILITY, 85));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles).isNotNull();
        assertThat(styles.expressionFor("distrust")).contains("aggressive");
    }

    @Test
    void highSuspiciousnessLowHostility_producesWatchfulDistrust() {
        var facets = new AmpdFacets(Map.of(
                AmpdFacets.SUSPICIOUSNESS, 80,
                AmpdFacets.HOSTILITY, 20));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles).isNotNull();
        assertThat(styles.expressionFor("distrust")).contains("watchful");
    }

    @Test
    void oppositeProfiles_produceDifferentTriumphExpressions() {
        var narcissist = new AmpdFacets(Map.of(
                AmpdFacets.GRANDIOSITY, 85,
                AmpdFacets.CALLOUSNESS, 90));
        var prosocial = new AmpdFacets(Map.of(
                AmpdFacets.GRANDIOSITY, 20,
                AmpdFacets.CALLOUSNESS, 15));

        var narcStyles = CognitiveDerivationEngine.deriveExpressionStyles(narcissist);
        var proStyles = CognitiveDerivationEngine.deriveExpressionStyles(prosocial);

        assertThat(narcStyles.expressionFor("triumph")).contains("theatrical");
        assertThat(proStyles.expressionFor("triumph")).contains("quiet");
    }

    @Test
    void nullFacets_returnsNull() {
        assertThat(CognitiveDerivationEngine.deriveExpressionStyles(null)).isNull();
    }

    @Test
    void emptyFacets_returnsNull() {
        assertThat(CognitiveDerivationEngine.deriveExpressionStyles(AmpdFacets.empty())).isNull();
    }

    @Test
    void deriveAndMerge_preservesExplicitExpressionStyles() {
        var explicit = CognitiveDefaults.empty("agent-1")
                .withExpressionStyles(new ExpressionStyleDefaults(Map.of("triumph", "custom override")))
                .withDescriptor(DescriptorView.of("agent-1", null, java.util.List.of(), java.util.List.of()));

        var merged = CognitiveDerivationEngine.deriveAndMerge(explicit);
        assertThat(merged.expressionStyles().expressionFor("triumph")).isEqualTo("custom override");
    }

    @Test
    void derive_includesExpressionStylesInFullDerivation() {
        var facets = new AmpdFacets(Map.of(
                AmpdFacets.GRANDIOSITY, 85,
                AmpdFacets.CALLOUSNESS, 90,
                AmpdFacets.MANIPULATIVENESS, 80));
        var descriptor = new DescriptorView("agent-1",
                new DispositionAxes("competitive", "strict", "bold", "high", "aggressive"),
                java.util.List.of(), java.util.List.of(), null, facets);

        var defaults = CognitiveDerivationEngine.derive(descriptor);

        assertThat(defaults.expressionStyles()).isNotNull();
        assertThat(defaults.expressionStyles().expressionFor("triumph")).contains("theatrical");
        assertThat(defaults.moodBaseline()).isNotNull();
    }

    @Test
    void highManipulativenessHighSuspiciousness_producesElaborateScheming() {
        var facets = new AmpdFacets(Map.of(
                AmpdFacets.MANIPULATIVENESS, 85,
                AmpdFacets.SUSPICIOUSNESS, 80));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles.expressionFor("scheming")).contains("elaborate");
    }

    @Test
    void highGrandiosity_lowCallousness_producesExpansiveTriumph() {
        var facets = new AmpdFacets(Map.of(
                AmpdFacets.GRANDIOSITY, 80,
                AmpdFacets.CALLOUSNESS, 20));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles.expressionFor("triumph")).contains("expansive");
    }
}
