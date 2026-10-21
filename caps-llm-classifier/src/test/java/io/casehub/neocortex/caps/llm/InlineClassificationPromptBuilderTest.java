package io.casehub.neocortex.caps.llm;

import io.casehub.neocortex.caps.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InlineClassificationPromptBuilderTest {

    private static final CapsTopology TOPOLOGY = new CapsTopology(1,
        Map.of(
            "reward", new CapsNode("reward", NodeType.INPUT, NodeRange.UNIPOLAR,
                "consequence", List.of(), Map.of("success", 0.8), "positive outcomes"),
            "threat_perception", new CapsNode("threat_perception", NodeType.INPUT, NodeRange.UNIPOLAR,
                "appraisal", List.of(), Map.of(), "perceived threats"),
            "approach", new CapsNode("approach", NodeType.OUTPUT, NodeRange.UNIPOLAR,
                "approach_avoidance", List.of(), Map.of(), "approach behavior")
        ),
        List.of(), Map.of(), List.of(),
        new WeightUpdateParameters(0.01, 0.25, 0.1, 0.5, 0.95,
            Map.of(), 0.2, 100, 0.001,
            new double[]{-2.0, 2.0}, new double[]{0.1, 3.0}, 0.6));

    @Test
    void buildsResponseFormatWithAssessmentBlock() {
        var builder = new InlineClassificationPromptBuilder(TOPOLOGY);
        var trajectory = List.of(
            new TrajectoryEntry(46, "Hooded Claw entered"),
            new TrajectoryEntry(47, "You overheard whispering")
        );
        var fragment = builder.buildAssessmentInstruction(trajectory);

        assertThat(fragment).contains("SITUATION ASSESSMENT");
        assertThat(fragment).contains("situationAssessment");
        assertThat(fragment).contains("reward");
        assertThat(fragment).contains("threat_perception");
        assertThat(fragment).doesNotContain("approach");
        assertThat(fragment).contains("Tick 46");
        assertThat(fragment).contains("Tick 47");
    }

    @Test
    void degreeOnlyInstructionOmitsAssessment() {
        var builder = new InlineClassificationPromptBuilder(TOPOLOGY);
        var instruction = builder.buildDegreeOnlyInstruction();

        assertThat(instruction).contains("situationChangeDegree");
        assertThat(instruction).doesNotContain("situationAssessment");
    }

    @Test
    void rendersTrajectoryWithTickNumbers() {
        var builder = new InlineClassificationPromptBuilder(TOPOLOGY);
        var trajectory = List.of(
            new TrajectoryEntry(10, "First event"),
            new TrajectoryEntry(11, "Second event")
        );
        var text = builder.renderTrajectoryText(trajectory);
        assertThat(text).contains("Tick 10: First event");
        assertThat(text).contains("Tick 11: Second event");
    }
}
