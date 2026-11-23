package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class DissonancePromptSection implements CognitionPromptRenderer {

    private final List<TensionSnapshot> tensions;
    private final DissonanceDetectionConfig config;

    public DissonancePromptSection(List<TensionSnapshot> tensions,
                                    DissonanceDetectionConfig config) {
        this.tensions = tensions;
        this.config = config;
    }

    @Override
    public BlockTag blockTag() { return BlockTag.DISSONANCE; }

    @Override
    public @Nullable String render(@Nullable CognitionRenderContext context) {
        var renderable = tensions.stream()
            .filter(t -> t.strength() >= config.renderingThreshold())
            .sorted(Comparator.comparingDouble(TensionSnapshot::strength).reversed())
            .limit(config.maxRendered())
            .toList();
        if (renderable.isEmpty()) return null;

        var sb = new StringBuilder();
        sb.append("You feel an uncomfortable tension between parts of yourself ")
          .append("that don't fit together:\n");
        for (var t : renderable) {
            sb.append("- ");
            if ("belief-behavior".equals(t.tensionType())) {
                sb.append("Your belief that ").append(t.elementAText())
                  .append(" sits uneasily alongside your established pattern of ")
                  .append(t.elementBText()).append(".");
            } else {
                sb.append("Your belief that ").append(t.elementAText())
                  .append(" pulls against your belief that ")
                  .append(t.elementBText()).append(".");
            }
            if (t.strength() >= 0.7) {
                sb.append(" This contradiction is sharp and unresolved.");
            }
            sb.append("\n");
        }
        return sb.toString().strip();
    }
}
