package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LearningProfileTest {

    @Test
    void extractsAssociativeReach() {
        var defaults = CognitiveDefaults.empty("agent-1")
                .withCapacityConfig(new CognitiveCapacityConfig(0.7, 0.8, 0.6));
        var profile = LearningProfile.from(defaults);
        assertThat(profile.associativeReach()).isEqualTo(0.8);
    }

    @Test
    void extractsRelationalComplexity() {
        var defaults = CognitiveDefaults.empty("agent-1")
                .withCapacityConfig(new CognitiveCapacityConfig(0.7, 0.8, 0.6));
        var profile = LearningProfile.from(defaults);
        assertThat(profile.relationalComplexity()).isEqualTo(0.7);
    }

    @Test
    void extractsSocialCognition() {
        var defaults = CognitiveDefaults.empty("agent-1")
                .withBehavioralDefaults(new BehavioralDefaults(null, null,
                        new SocialCognitionDefaults(0.6, ConflictInterpretation.REPAIR), null));
        var profile = LearningProfile.from(defaults);
        assertThat(profile.trustFormationRate()).isEqualTo(0.6);
        assertThat(profile.conflictInterpretation()).isEqualTo(ConflictInterpretation.REPAIR);
    }

    @Test
    void extractsDispositionAxes() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "analytical");
        var view = DescriptorView.of("agent-1", axes, List.of(), List.of());
        var defaults = CognitiveDefaults.empty("agent-1").withDescriptor(view);
        var profile = LearningProfile.from(defaults);
        assertThat(profile.dispositionAxes()).isEqualTo(axes);
    }

    @Test
    void extractsCopingDefaults() {
        var coping = new CopingDefaults(0.6, 0.5, 0.4, 0.3, 0.7, 0.5, 0.4, 0.3);
        var defaults = CognitiveDefaults.empty("agent-1")
                                        .withCopingDefaults(coping);
        var profile = LearningProfile.from(defaults);
        assertThat(profile.copingDefaults()).isEqualTo(coping);
    }

    @Test
    void nullSafeOnEmptyDefaults() {
        var profile = LearningProfile.from(CognitiveDefaults.empty("agent-1"));
        assertThat(profile.associativeReach()).isNull();
        assertThat(profile.trustFormationRate()).isNull();
        assertThat(profile.dispositionAxes()).isNull();
        assertThat(profile.appraisalWeights()).isNull();
    }

    @Test
    void exposesPersonalityFacets() {
        var facets = List.of(new PersonalityFacet("Anxiousness", 0.7, "eidos"));
        var view = new DescriptorView("a1", null, List.of(), List.of(), null, facets);
        var defaults = CognitiveDefaults.empty("a1").withDescriptor(view);
        var profile = LearningProfile.from(defaults);
        assertThat(profile.facets()).isEqualTo(facets);
    }

    @Test
    void exposesRegulationDefaults() {
        var reg = new RegulationDefaults(0.8, 0.5, 0.3, 0.6);
        var defaults = CognitiveDefaults.empty("a1").withRegulationDefaults(reg);
        var profile = LearningProfile.from(defaults);
        assertThat(profile.regulationDefaults()).isEqualTo(reg);
    }

    @Test
    void exposesAppraisalWeights() {
        var aw       = new io.casehub.neocortex.mindmap.AppraisalWeights(1.2, 0.8, 0.9, 1.1, 0.7);
        var defaults = CognitiveDefaults.empty("a1").withAppraisalWeights(aw);
        var profile  = LearningProfile.from(defaults);
        assertThat(profile.appraisalWeights()).isEqualTo(aw);
    }


    @Test
    void exposesAgentId() {
        var profile = LearningProfile.from(CognitiveDefaults.empty("my-agent"));
        assertThat(profile.agentId()).isEqualTo("my-agent");
    }
}
