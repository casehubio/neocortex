package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RegulationDispositionMappingTest {

    private final CapsTopology topology =
        new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");

    @Test
    void avoidantConflictModeBoostsSituationSelection() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated",
            "moderate", "avoidant");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "a1", axes);
        var situationSelection = state.nodeStates().get("situation_selection");
        assertThat(situationSelection).isNotNull();
        assertThat(situationSelection.restingActivation()).isCloseTo(0.3, within(0.01));
    }

    @Test
    void analyticalConflictModeBoostsCognitiveChange() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated",
            "moderate", "analytical");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "a1", axes);
        var cognitiveChange = state.nodeStates().get("cognitive_change");
        assertThat(cognitiveChange).isNotNull();
        assertThat(cognitiveChange.restingActivation()).isCloseTo(0.3, within(0.01));
    }

    @Test
    void accommodatingConflictModeBoostsCognitiveChange() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated",
                                       "moderate", "accommodating");
        var mapper          = new DispositionWeightMapper(topology);
        var state           = mapper.initializeWeights("t1", "a1", axes);
        var cognitiveChange = state.nodeStates().get("cognitive_change");
        assertThat(cognitiveChange).isNotNull();
        assertThat(cognitiveChange.restingActivation()).isCloseTo(0.25, within(0.01));
    }


    @Test
    void cooperativeSocialOrientBoostsSocialSupportSeeking() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated",
            "moderate", "cooperative");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "a1", axes);
        var supportSeeking = state.nodeStates().get("social_support_seeking");
        assertThat(supportSeeking).isNotNull();
        assertThat(supportSeeking.restingActivation()).isCloseTo(0.25, within(0.01));
    }

    @Test
    void competitiveConflictModeBoostsResponseModulation() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated",
            "moderate", "competitive");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "a1", axes);
        var responseModulation = state.nodeStates().get("response_modulation");
        assertThat(responseModulation).isNotNull();
        assertThat(responseModulation.restingActivation()).isCloseTo(0.25, within(0.01));
    }

    @Test
    void conservativeRiskBoostsSituationSelection() {
        var axes = new DispositionAxes("independent", "moderate", "conservative",
            "moderate", "cooperative");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "a1", axes);
        var situationSelection = state.nodeStates().get("situation_selection");
        assertThat(situationSelection).isNotNull();
        assertThat(situationSelection.restingActivation()).isCloseTo(0.2, within(0.01));
    }

    @Test
    void boldRiskBoostsSituationModification() {
        var axes = new DispositionAxes("independent", "moderate", "bold",
            "moderate", "cooperative");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "a1", axes);
        var situationModification = state.nodeStates().get("situation_modification");
        assertThat(situationModification).isNotNull();
        assertThat(situationModification.restingActivation()).isCloseTo(0.3, within(0.01));
    }

    @Test
    void independentSocialOrientBoostsResponseModulation() {
        var axes = new DispositionAxes("independent", "moderate", "calculated",
            "moderate", "cooperative");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "a1", axes);
        var responseModulation = state.nodeStates().get("response_modulation");
        assertThat(responseModulation).isNotNull();
        assertThat(responseModulation.restingActivation()).isCloseTo(0.2, within(0.01));
    }

    @Test
    void competitiveConflictModeSetsExistingBehavioralAttractors() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated",
            "moderate", "competitive");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "a1", axes);
        var fight = state.nodeStates().get("fight");
        assertThat(fight).isNotNull();
        assertThat(fight.restingActivation()).isCloseTo(0.15, within(0.01));
    }
}
