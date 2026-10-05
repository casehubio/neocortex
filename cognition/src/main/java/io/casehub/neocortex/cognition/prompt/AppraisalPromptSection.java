package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.appraisal.ActionTendency;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import org.jspecify.annotations.Nullable;

public class AppraisalPromptSection implements CognitionPromptRenderer {

    private final AppraisalTickParticipant participant;

    public AppraisalPromptSection(AppraisalTickParticipant participant) {
        this.participant = participant;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        return participant.currentResult(context.agentId(), context.tenantId())
                .map(AppraisalPromptSection::renderEvocative)
                .orElse(null);
    }

    private static @Nullable String renderEvocative(AppraisalResult result) {
        var sb = new StringBuilder();

        if (!result.emotions().isEmpty()) {
            sb.append("You feel ");
            var emotions = result.emotions();
            for (int i = 0; i < emotions.size(); i++) {
                if (i > 0) sb.append(i == emotions.size() - 1 ? " and " : ", ");
                sb.append(describeEmotion(emotions.get(i)));
            }
            sb.append(".\n");
        }

        result.actionTendencies().stream()
                .filter(t -> t.intensity() > 0.3)
                .forEach(t -> sb.append(describeTendency(t)).append("\n"));

        return sb.isEmpty() ? null : sb.toString().strip();
    }

    private static String describeEmotion(CognitiveEmotion emotion) {
        var intensity = emotion.intensity() > 0.7 ? "strong "
                : emotion.intensity() > 0.4 ? "" : "mild ";
        return intensity + emotion.type().name().toLowerCase().replace('_', ' ');
    }

    private static String describeTendency(ActionTendency t) {
        return switch (t.readiness()) {
            case APPROACH -> "You feel drawn toward " + t.target() + ".";
            case AVOIDANCE -> "You want to get away from " + t.target() + ".";
            case ATTENDING -> "Your attention is fixed on " + t.target() + ".";
            case REJECTION -> "You feel repelled by " + t.target() + ".";
            case ANTAGONISM -> "You feel combative toward " + t.target() + ".";
            case INTERRUPTION -> "Your attention wanders — you want something new.";
            case SUBMISSION -> "You feel like yielding to " + t.target() + ".";
            case DOMINANCE -> "You feel like asserting control over " + t.target() + ".";
            case INDIFFERENCE -> "You feel nothing about " + t.target() + ".";
        };
    }
}
