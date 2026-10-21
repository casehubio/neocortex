package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationValidatorTest {

    private static final CapsTopology TOPOLOGY = new CapsTopology(1,
        Map.of(
            "reward", new CapsNode("reward", NodeType.INPUT, NodeRange.UNIPOLAR,
                "consequence", List.of(), Map.of(), "positive outcomes"),
            "punishment", new CapsNode("punishment", NodeType.INPUT, NodeRange.UNIPOLAR,
                "consequence", List.of(), Map.of(), "negative outcomes"),
            "approach", new CapsNode("approach", NodeType.OUTPUT, NodeRange.UNIPOLAR,
                "approach_avoidance", List.of(), Map.of(), "approach behavior")
        ),
        List.of(), Map.of(), List.of(),
        new WeightUpdateParameters(0.01, 0.25, 0.1, 0.5, 0.95,
            Map.of(), 0.2, 100, 0.001,
            new double[]{-2.0, 2.0}, new double[]{0.1, 3.0}, 0.6));

    @Test
    void filtersInvalidNodeIds() {
        var validator = new ClassificationValidator(TOPOLOGY);
        var activations = List.of(
            new SituationActivation("reward", 0.8),
            new SituationActivation("nonexistent", 0.9)
        );
        var ctx = validator.validate(activations);
        assertThat(ctx.activations()).hasSize(1);
        assertThat(ctx.activations().getFirst().nodeId()).isEqualTo("reward");
    }

    @Test
    void filtersOutputNodes() {
        var validator = new ClassificationValidator(TOPOLOGY);
        var activations = List.of(
            new SituationActivation("reward", 0.8),
            new SituationActivation("approach", 0.7)
        );
        var ctx = validator.validate(activations);
        assertThat(ctx.activations()).hasSize(1);
        assertThat(ctx.activations().getFirst().nodeId()).isEqualTo("reward");
    }

    @Test
    void filtersBelowConfidenceThreshold() {
        var validator = new ClassificationValidator(TOPOLOGY);
        var activations = List.of(
            new SituationActivation("reward", 0.8),
            new SituationActivation("punishment", 0.2)
        );
        var ctx = validator.validate(activations);
        assertThat(ctx.activations()).hasSize(1);
        assertThat(ctx.activations().getFirst().nodeId()).isEqualTo("reward");
    }

    @Test
    void clampsConfidenceToOne() {
        var validator = new ClassificationValidator(TOPOLOGY);
        var activations = List.of(new SituationActivation("reward", 1.5));
        var ctx = validator.validate(activations);
        assertThat(ctx.activations().getFirst().confidence()).isEqualTo(1.0);
    }

    @Test
    void emptyInputReturnsEmpty() {
        var validator = new ClassificationValidator(TOPOLOGY);
        var ctx = validator.validate(List.of());
        assertThat(ctx.isEmpty()).isTrue();
    }

    @Test
    void validInputNodeIdsReturnsOnlyInputNodes() {
        var validator = new ClassificationValidator(TOPOLOGY);
        assertThat(validator.validInputNodeIds()).containsExactlyInAnyOrder("reward", "punishment");
    }

    @Test
    void buildNodeVocabularyRendersInputNodes() {
        var validator = new ClassificationValidator(TOPOLOGY);
        var vocab = validator.buildNodeVocabulary();
        assertThat(vocab).contains("reward");
        assertThat(vocab).contains("punishment");
        assertThat(vocab).doesNotContain("approach");
    }
}
