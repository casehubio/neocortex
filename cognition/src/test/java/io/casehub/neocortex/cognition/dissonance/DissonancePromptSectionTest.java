package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DissonancePromptSectionTest {

    private static final DissonanceDetectionConfig CONFIG =
        new DissonanceDetectionConfig(true, 0.1, 0.15, 0.3, 3,
            0.15, 0.3, 0.10, 0.2, 0.05, 0.1, 0.5);

    @Test
    void rendersBeliefBeliefTension() {
        var tensions = List.of(new TensionSnapshot(
            "n1", "belief-belief", "a", "b",
            "I am a loyal friend",
            "I would betray anyone for power",
            0.85, "reason", Map.of(), Map.of()));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertThat(text).contains("loyal friend");
        assertThat(text).contains("betray");
    }

    @Test
    void rendersBeliefBehaviorWithActionGapWording() {
        var tensions = List.of(new TensionSnapshot(
            "n1", "belief-behavior", "a", "b",
            "honesty matters",
            "deliberate_provocation (driven by dominance)",
            0.70, "reason", Map.of(), Map.of()));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertThat(text).contains("pattern of");
    }

    @Test
    void filtersBelowRenderingThreshold() {
        var tensions = List.of(
            new TensionSnapshot("n1", "belief-belief", "a", "b",
                "belief-a", "belief-b", 0.20, "reason", Map.of(), Map.of()));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertThat(text).isNull();
    }

    @Test
    void sortsStrongestFirst() {
        var tensions = List.of(
            new TensionSnapshot("n1", "belief-belief", "a", "b",
                "weak-a", "weak-b", 0.4, "r", Map.of(), Map.of()),
            new TensionSnapshot("n2", "belief-belief", "c", "d",
                "strong-a", "strong-b", 0.9, "r", Map.of(), Map.of()));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertThat(text.indexOf("strong")).isLessThan(text.indexOf("weak"));
    }

    @Test
    void capsAtMaxRendered() {
        var tensions = List.of(
            snapshot(0.9, "a1", "b1"),
            snapshot(0.8, "a2", "b2"),
            snapshot(0.7, "a3", "b3"),
            snapshot(0.6, "a4", "b4"));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertThat(text).doesNotContain("a4");
    }

    @Test
    void blockTagIsDissonance() {
        var section = new DissonancePromptSection(List.of(), CONFIG);
        assertThat(section.blockTag()).isEqualTo(BlockTag.DISSONANCE);
    }

    private TensionSnapshot snapshot(double strength, String a, String b) {
        return new TensionSnapshot("n", "belief-belief",
                                   "id-" + a, "id-" + b, a, b, strength, "r",
                                   Map.of(), Map.of());
    }
}
