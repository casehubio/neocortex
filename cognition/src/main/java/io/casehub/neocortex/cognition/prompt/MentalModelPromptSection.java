package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.mentalmodel.AttributedState;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class MentalModelPromptSection implements CognitionPromptRenderer {

    private final MentalModelOrchestrator mentalModel;

    public MentalModelPromptSection(MentalModelOrchestrator mentalModel) {
        this.mentalModel = mentalModel;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        var snapshots = mentalModel.activeSnapshots(context.agentId(), context.tenantId());
        if (snapshots.isEmpty()) {
            return null;
        }
        var target = context.subjectId() != null
                ? snapshots.stream().filter(s -> s.subjectId().equals(context.subjectId())).findFirst().orElse(null)
                : snapshots.get(0);
        if (target == null) {
            return null;
        }
        return render(target);
    }

    private static @Nullable String render(MentalModelSnapshot snapshot) {
        var sb = new StringBuilder();
        appendDimension(sb, "What you believe about the user", snapshot.beliefs());
        appendDimension(sb, "What you think they want", snapshot.desires());
        appendDimension(sb, "Their likely intentions", snapshot.intentions());
        if (sb.isEmpty()) {
            return null;
        }
        return "Theory of Mind (" + snapshot.subjectId() + "):" + sb;
    }

    private static void appendDimension(StringBuilder sb, String label, List<AttributedState> states) {
        var relevant = states.stream().filter(s -> s.confidence() >= 0.3).toList();
        if (!relevant.isEmpty()) {
            sb.append("\n").append(label).append(":");
            for (var state : relevant) {
                sb.append("\n  - ").append(state.description())
                  .append(" (confidence: ").append(String.format("%.1f", state.confidence())).append(")");
            }
        }
    }
}
