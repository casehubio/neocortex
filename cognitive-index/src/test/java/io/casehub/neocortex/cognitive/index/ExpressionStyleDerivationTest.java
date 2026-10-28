package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionStyleDerivationTest {

    @Test
    void highAntagonismTriad_producesTheatricalTriumphAndSavoringGloat() {
        var facets = List.of(
                new PersonalityFacet("Grandiosity", 0.85, null),
                new PersonalityFacet("Callousness", 0.90, null),
                new PersonalityFacet("Manipulativeness", 0.80, null));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles).isNotNull();
        assertThat(styles.expressionFor("triumph")).contains("theatrical");
        assertThat(styles.expressionFor("gloating")).contains("savoring");
    }

    @Test
    void lowCallousness_producesGenuineGallantryAndSuppressedGloat() {
        var facets = List.of(
                new PersonalityFacet("Callousness", 0.15, null),
                new PersonalityFacet("Suspiciousness", 0.20, null),
                new PersonalityFacet("Grandiosity", 0.25, null));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles).isNotNull();
        assertThat(styles.expressionFor("gallantry")).contains("genuine");
        assertThat(styles.expressionFor("gloating")).contains("uncomfortable");
    }

    @Test
    void highSuspiciousnessHighHostility_producesAggressiveDistrust() {
        var facets = List.of(
                new PersonalityFacet("Suspiciousness", 0.80, null),
                new PersonalityFacet("Hostility", 0.85, null));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles).isNotNull();
        assertThat(styles.expressionFor("distrust")).contains("aggressive");
    }

    @Test
    void highSuspiciousnessLowHostility_producesWatchfulDistrust() {
        var facets = List.of(
                new PersonalityFacet("Suspiciousness", 0.80, null),
                new PersonalityFacet("Hostility", 0.20, null));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles).isNotNull();
        assertThat(styles.expressionFor("distrust")).contains("watchful");
    }

    @Test
    void oppositeProfiles_produceDifferentTriumphExpressions() {
        var narcissist = List.of(
                new PersonalityFacet("Grandiosity", 0.85, null),
                new PersonalityFacet("Callousness", 0.90, null));
        var prosocial = List.of(
                new PersonalityFacet("Grandiosity", 0.20, null),
                new PersonalityFacet("Callousness", 0.15, null));

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
        assertThat(CognitiveDerivationEngine.deriveExpressionStyles(List.of())).isNull();
    }

    @Test
    void deriveAndMerge_preservesExplicitExpressionStyles() {
        var explicit = CognitiveDefaults.empty("agent-1")
                .withExpressionStyles(new ExpressionStyleDefaults(Map.of("triumph", "custom override")))
                .withDescriptor(DescriptorView.of("agent-1", null, List.of(), List.of()));

        var merged = CognitiveDerivationEngine.deriveAndMerge(explicit);
        assertThat(merged.expressionStyles().expressionFor("triumph")).isEqualTo("custom override");
    }

    @Test
    void derive_includesExpressionStylesInFullDerivation() {
        var facets = List.of(
                new PersonalityFacet("Grandiosity", 0.85, null),
                new PersonalityFacet("Callousness", 0.90, null),
                new PersonalityFacet("Manipulativeness", 0.80, null));
        var descriptor = new DescriptorView("agent-1",
                new DispositionAxes("competitive", "strict", "bold", "high", "aggressive"),
                List.of(), List.of(), null, facets);

        var defaults = CognitiveDerivationEngine.derive(descriptor);

        assertThat(defaults.expressionStyles()).isNotNull();
        assertThat(defaults.expressionStyles().expressionFor("triumph")).contains("theatrical");
        assertThat(defaults.moodBaseline()).isNotNull();
    }

    @Test
    void highManipulativenessHighSuspiciousness_producesElaborateScheming() {
        var facets = List.of(
                new PersonalityFacet("Manipulativeness", 0.85, null),
                new PersonalityFacet("Suspiciousness", 0.80, null));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles.expressionFor("scheming")).contains("elaborate");
    }

    @Test
    void highGrandiosity_lowCallousness_producesExpansiveTriumph() {
        var facets = List.of(
                new PersonalityFacet("Grandiosity", 0.80, null),
                new PersonalityFacet("Callousness", 0.20, null));
        var styles = CognitiveDerivationEngine.deriveExpressionStyles(facets);

        assertThat(styles.expressionFor("triumph")).contains("expansive");
    }
}
