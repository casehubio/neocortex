package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant;
import io.casehub.neocortex.cognition.appraisal.ChannelWeightProfile;
import org.jspecify.annotations.Nullable;

/**
 * Renders situation-dependent channel emphasis guidance per
 * Easterbrook (1959) attentional narrowing under arousal.
 *
 * <p>Only renders when channel weights deviate from the default
 * equal profile — the emphasis is the delta, not the baseline.
 */
public class ChannelEmphasisPromptSection implements CognitionPromptRenderer {

    private static final double EMPHASIS_THRESHOLD = 0.30;
    private static final double SUPPRESS_THRESHOLD = 0.18;

    private final AppraisalTickParticipant participant;

    public ChannelEmphasisPromptSection(AppraisalTickParticipant participant) {
        this.participant = participant;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        var weightsOpt = participant.currentChannelWeights(
                context.agentId(), context.tenantId());
        if (weightsOpt.isEmpty()) return null;

        var weights = weightsOpt.get();
        if (weights.isDefault()) return null;

        var sb = new StringBuilder();
        sb.append("Right now, your inner experience is shaped by this situation:\n");

        appendChannel(sb, "somatic", weights.somatic(),
                "physical sensations — gut reactions, muscle tension, breathing, temperature",
                "your body's quiet background");
        appendChannel(sb, "attentional", weights.attentional(),
                "what captures your focus — what stands out, what you can't look away from",
                "your broad, unfocused awareness");
        appendChannel(sb, "temporal", weights.temporal(),
                "your experience of time — compressed, expanded, suspended, anticipating",
                "the ordinary flow of time");
        appendChannel(sb, "action tendency", weights.actionTendency(),
                "behavioral urges — what you feel pulled to do, approach, retreat from, ready yourself for",
                "the absence of any strong pull to act");

        return sb.toString().strip();
    }

    private void appendChannel(StringBuilder sb, String name, double weight,
                                String emphasisDesc, String suppressDesc) {
        if (weight >= EMPHASIS_THRESHOLD) {
            sb.append("- Foreground your ").append(name).append(": ")
              .append(emphasisDesc).append(".\n");
        } else if (weight <= SUPPRESS_THRESHOLD) {
            sb.append("- Background your ").append(name).append(": ")
              .append(suppressDesc).append(".\n");
        }
    }
}
