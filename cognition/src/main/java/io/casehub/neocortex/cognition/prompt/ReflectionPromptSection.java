package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.memory.ReflectionEntry;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class ReflectionPromptSection implements CognitionPromptRenderer {

    private static final int MAX_ITEMS = 5;
    private final List<ReflectionEntry> entries;

    public ReflectionPromptSection(List<ReflectionEntry> entries) {
        this.entries = entries;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        if (entries.isEmpty()) return null;
        var sb = new StringBuilder("Learned heuristics:");
        var limit = Math.min(entries.size(), MAX_ITEMS);
        for (int i = 0; i < limit; i++) {
            sb.append("\n- ").append(entries.get(i).insight());
        }
        return sb.toString();
    }
}
