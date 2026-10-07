package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.HabituationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CognitiveDerivationEngineHabituationTest {

    @Test
    void boldRisk_fastHabituation() {
        var axes = new DispositionAxes("cooperative", "moderate", "bold", "moderate", "cooperative");
        var descriptor = DescriptorView.of("agent-h1", axes, List.of(), List.of());

        var result = CognitiveDerivationEngine.derive(descriptor);

        assertThat(result.habituationConfig()).isNotNull();
        assertThat(result.habituationConfig().habituationRate()).isGreaterThan(0.3);
    }

    @Test
    void conservativeRisk_slowHabituation() {
        var axes = new DispositionAxes("cooperative", "moderate", "conservative", "moderate", "cooperative");
        var descriptor = DescriptorView.of("agent-h2", axes, List.of(), List.of());

        var result = CognitiveDerivationEngine.derive(descriptor);

        assertThat(result.habituationConfig()).isNotNull();
        assertThat(result.habituationConfig().habituationRate()).isLessThan(0.15);
    }

    @Test
    void flexibleRules_lowRepetitionTolerance() {
        var axes = new DispositionAxes("cooperative", "flexible", "calculated", "moderate", "cooperative");
        var descriptor = DescriptorView.of("agent-h3", axes, List.of(), List.of());

        var result = CognitiveDerivationEngine.derive(descriptor);

        assertThat(result.habituationConfig()).isNotNull();
        assertThat(result.habituationConfig().repetitionTolerance()).isLessThan(5.0);
    }

    @Test
    void strictRules_highRepetitionTolerance() {
        var axes = new DispositionAxes("cooperative", "strict", "calculated", "moderate", "cooperative");
        var descriptor = DescriptorView.of("agent-h4", axes, List.of(), List.of());

        var result = CognitiveDerivationEngine.derive(descriptor);

        assertThat(result.habituationConfig()).isNotNull();
        assertThat(result.habituationConfig().repetitionTolerance()).isGreaterThan(7.0);
    }

    @Test
    void highAutonomy_reducesRepetitionTolerance() {
        var axesHigh = new DispositionAxes("cooperative", "moderate", "calculated", "high", "cooperative");
        var axesMod = new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative");

        var high = CognitiveDerivationEngine.derive(DescriptorView.of("agent-h5a", axesHigh, List.of(), List.of()));
        var mod = CognitiveDerivationEngine.derive(DescriptorView.of("agent-h5b", axesMod, List.of(), List.of()));

        assertThat(high.habituationConfig().repetitionTolerance())
                .isLessThan(mod.habituationConfig().repetitionTolerance());
    }

    @Test
    void nullDisposition_noHabituationConfig() {
        var descriptor = DescriptorView.of("agent-h6", null, List.of(), List.of());

        var result = CognitiveDerivationEngine.derive(descriptor);

        assertThat(result.habituationConfig()).isNull();
    }

    @Test
    void goalsProduceDomainModulation() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative");
        var descriptor = DescriptorView.of("agent-h7", axes, List.of(), List.of("advance career"));

        var result = CognitiveDerivationEngine.derive(descriptor);

        assertThat(result.habituationConfig()).isNotNull();
        assertThat(result.habituationConfig().domainModulation()).isNotEmpty();
        assertThat(result.habituationConfig().domainModulation()).containsKey("PROJECT");
    }

    @Test
    void deriveAndMerge_explicitHabituationWins() {
        var axes = new DispositionAxes("cooperative", "moderate", "bold", "moderate", "cooperative");
        var explicitHab = new HabituationConfig(0.9, 0.8, 2.0, Map.of());
        var explicit = CognitiveDefaults.empty("agent-h8")
                .withTenantId("t1")
                .withHabituationConfig(explicitHab)
                .withDescriptor(DescriptorView.of("agent-h8", axes, List.of(), List.of()));

        var result = CognitiveDerivationEngine.deriveAndMerge(explicit);

        assertThat(result.habituationConfig().habituationRate()).isCloseTo(0.9, within(0.01));
    }

    @Test
    void deriveAndMerge_fallsThroughToDerivedHabituation() {
        var axes = new DispositionAxes("cooperative", "strict", "bold", "high", "cooperative");
        var explicit = CognitiveDefaults.empty("agent-h9")
                .withTenantId("t1")
                .withDescriptor(DescriptorView.of("agent-h9", axes, List.of(), List.of()));

        var result = CognitiveDerivationEngine.deriveAndMerge(explicit);

        assertThat(result.habituationConfig()).isNotNull();
        assertThat(result.habituationConfig().habituationRate()).isGreaterThan(0.3);
    }
}
