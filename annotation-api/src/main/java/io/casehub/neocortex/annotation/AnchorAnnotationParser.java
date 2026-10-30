package io.casehub.neocortex.annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnchorAnnotationParser {

    private static final Pattern ANCHOR_PATTERN = Pattern.compile("(\\S+)\\[([a-zA-Z0-9_-]+)]");

    public AnchorParseResult parse(String text) {
        if (text == null || text.isEmpty()) return new AnchorParseResult("", List.of());

        List<ParsedAnchor> anchors = new ArrayList<>();
        Matcher matcher = ANCHOR_PATTERN.matcher(text);
        StringBuilder clean = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            clean.append(text, lastEnd, matcher.start());
            String surfaceForm = matcher.group(1);
            String alias = matcher.group(2);
            int cleanStart = clean.length();
            clean.append(surfaceForm);
            int cleanEnd = clean.length();
            anchors.add(new ParsedAnchor(alias, surfaceForm, cleanStart, cleanEnd));
            lastEnd = matcher.end();
        }

        clean.append(text, lastEnd, text.length());
        return new AnchorParseResult(clean.toString(), List.copyOf(anchors));
    }
}
