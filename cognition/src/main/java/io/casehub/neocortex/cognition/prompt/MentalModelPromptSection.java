package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.mentalmodel.AttributedState;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelSnapshot;
import io.casehub.neocortex.cognition.mentalmodel.TheoryOfMindPosterior;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
        var tomPosteriors = mentalModel.activeTomPosteriors(context.agentId(), context.tenantId());
        var target = context.subjectId() != null
                ? snapshots.stream().filter(s -> s.subjectId().equals(context.subjectId())).findFirst().orElse(null)
                : snapshots.get(0);
        if (target == null) {
            return null;
        }
        var posterior = tomPosteriors.get(target.subjectId());
        return render(target, posterior);
    }

    private static @Nullable String render(MentalModelSnapshot snapshot,
                                           @Nullable TheoryOfMindPosterior posterior) {
        var sb = new StringBuilder();
        appendDimension(sb, "What you believe about the user", snapshot.beliefs());
        appendDimension(sb, "What you think they want", snapshot.desires());
        appendDimension(sb, "Their likely intentions", snapshot.intentions());
        if (posterior != null && posterior.dominantProbability() > 0.6) {
            appendPosterior(sb, posterior);
        }
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

    private static void appendPosterior(StringBuilder sb, TheoryOfMindPosterior posterior) {
        sb.append("\nInferred emotional state (Bayesian):");
        sb.append("\n  - positive: ").append(String.format("%.0f%%", posterior.positive() * 100));
        sb.append(", neutral: ").append(String.format("%.0f%%", posterior.neutral() * 100));
        sb.append(", distressed: ").append(String.format("%.0f%%", posterior.distressed() * 100));
        sb.append(" → likely ").append(posterior.dominantValence().toLowerCase());
    }
}
