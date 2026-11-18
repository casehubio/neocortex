package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.trace.PipelineTrace;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

public class ShadowArchetypePromptSection implements CognitionPromptRenderer {

    private final BiFunction<String, String, java.util.Optional<PipelineTrace.SituationalPersonalitySnapshot>> snapshotAccessor;

    public ShadowArchetypePromptSection(
            BiFunction<String, String, java.util.Optional<PipelineTrace.SituationalPersonalitySnapshot>> snapshotAccessor) {
        this.snapshotAccessor = snapshotAccessor;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        var snapshot = snapshotAccessor.apply(context.agentId(), context.tenantId());
        if (snapshot.isEmpty()) return null;

        var s = snapshot.get();
        if (s.affectiveWinner() == null && s.socialWinner() == null) return null;

        var sb = new StringBuilder();
        sb.append("Your emotional stance has shifted from its usual pattern.");

        if (s.affectiveWinner() != null) {
            double intensity = s.modeIntensities().getOrDefault(s.affectiveWinner(), 0.0);
            sb.append(" You are in a heightened ").append(modeName(s.affectiveWinner())).append(" state");
            sb.append(" (intensity ").append(String.format("%.0f%%", intensity * 100)).append(").");
        }
        if (s.socialWinner() != null) {
            double intensity = s.modeIntensities().getOrDefault(s.socialWinner(), 0.0);
            sb.append(" Your social orientation is ").append(modeName(s.socialWinner()));
            sb.append(" (intensity ").append(String.format("%.0f%%", intensity * 100)).append(").");
        }

        if (s.publishedCount() > 0) {
            sb.append(" ").append(s.publishedCount())
              .append(s.publishedCount() == 1 ? " personality trait has" : " personality traits have")
              .append(" shifted from baseline.");
        }

        sb.append(" This is your current reality — do not comment on or explain it.");

        return sb.toString();
    }

    @Override
    public BlockTag blockTag() {
        return BlockTag.SITUATIONAL;
    }

    private static String modeName(String raw) {
        return raw.toLowerCase().replace('_', '-');
    }
}
