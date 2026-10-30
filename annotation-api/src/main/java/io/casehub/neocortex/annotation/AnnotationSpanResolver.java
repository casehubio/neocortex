package io.casehub.neocortex.annotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AnnotationSpanResolver {

    public List<ResolvedSpan> resolve(String text, List<MentionInput> mentions) {
        if (mentions.isEmpty()) return List.of();

        List<MentionInput> sorted = new ArrayList<>(mentions);
        sorted.sort(Comparator.comparingInt(MentionInput::mentionIndex));

        List<ResolvedSpan> result = new ArrayList<>();
        int scanPos = 0;

        for (MentionInput m : sorted) {
            int idx = text.indexOf(m.surfaceForm(), scanPos);
            if (idx < 0) continue;
            result.add(new ResolvedSpan(
                m.entityName(), m.surfaceForm(),
                idx, idx + m.surfaceForm().length(),
                m.mentionIndex()));
            scanPos = idx + m.surfaceForm().length();
        }

        return List.copyOf(result);
    }
}
