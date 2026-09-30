package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import org.jspecify.annotations.Nullable;

public class AttentionPromptSection implements CognitionPromptRenderer {

    private final AttentionBriefing briefing;

    public AttentionPromptSection(AttentionBriefing briefing) {
        this.briefing = briefing;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        var signals = briefing.signals();
        if (signals.isEmpty()) return null;
        var sb = new StringBuilder("Attention required:");
        for (var signal : signals) {
            sb.append("\n- [").append(signal.category().name()).append("] ")
              .append(signal.sourceName());
            if (signal.significance() > 0) {
                sb.append(" (").append(String.format("%.2f", signal.significance())).append(")");
            }
            sb.append(": ").append(signal.reason());
        }
        return sb.toString();
    }
}
