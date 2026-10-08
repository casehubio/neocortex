package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public class SubThoughtPromptSection implements CognitionPromptRenderer {

    private static final int MAX_SUB_THOUGHTS = 10;
    private final SubThoughtTickParticipant participant;

    public SubThoughtPromptSection(SubThoughtTickParticipant participant) {
        this.participant = participant;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        var result = participant.currentSubThoughts(context.agentId(), context.tenantId());
        if (result == null || result.isEmpty()) return null;

        var sorted = result.subThoughts().stream()
                .sorted(Comparator.comparingDouble(SubThought::confidence).reversed())
                .limit(MAX_SUB_THOUGHTS)
                .toList();

        var grouped = sorted.stream()
                .collect(Collectors.groupingBy(
                        st -> st.entity() != null ? st.entity() : "General",
                        LinkedHashMap::new,
                        Collectors.toList()));

        var sb = new StringBuilder("## Recent Cognitive Reactions\n\n");
        for (var entry : grouped.entrySet()) {
            String label = "General".equals(entry.getKey()) ? "General:" : "About " + entry.getKey() + ":";
            sb.append(label).append('\n');
            for (var st : entry.getValue()) {
                sb.append("- ").append(st.text()).append(" (").append(st.type()).append(")\n");
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }
}
