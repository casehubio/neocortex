package io.casehub.neocortex.corpus.management;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlockAnnotationParser {

    private static final Pattern ANNOTATION_LINE = Pattern.compile("^#@\\s*(\\w[\\w-]*):\\s*(.*)$");
    private static final Pattern KV_PAIR = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^\\s]+))");

    public BlockParseResult parse(String text) {
        if (text == null || text.isEmpty()) return new BlockParseResult("", List.of());

        String[] lines = text.split("\n", -1);
        StringBuilder clean = new StringBuilder();
        List<PendingAnnotation> pending = new ArrayList<>();
        boolean firstCleanLine = true;

        for (String line : lines) {
            Matcher m = ANNOTATION_LINE.matcher(line);
            if (m.matches()) {
                String type = m.group(1);
                String rawValue = m.group(2).strip();
                String value = extractValue(rawValue);
                Map<String, String> params = parseParams(rawValue);
                pending.add(new PendingAnnotation(type, value, params, clean.length()));
            } else {
                if (!firstCleanLine) {
                    clean.append("\n");
                }
                clean.append(line);
                firstCleanLine = false;
            }
        }

        String cleanText = clean.toString();
        List<BlockAnnotation> annotations = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            PendingAnnotation p = pending.get(i);
            int endOffset = (i + 1 < pending.size())
                ? pending.get(i + 1).offset
                : cleanText.length();
            annotations.add(new BlockAnnotation(p.type, p.value, p.params, p.offset, endOffset));
        }

        return new BlockParseResult(cleanText, annotations);
    }

    private static String extractValue(String raw) {
        if (raw.isEmpty()) return "";
        Matcher kvMatcher = KV_PAIR.matcher(raw);
        if (kvMatcher.find()) {
            String beforeKv = raw.substring(0, kvMatcher.start()).strip();
            if (!beforeKv.isEmpty()) return unquote(beforeKv);
            return "";
        }
        return unquote(raw);
    }

    private static Map<String, String> parseParams(String raw) {
        Map<String, String> params = new LinkedHashMap<>();
        Matcher m = KV_PAIR.matcher(raw);
        while (m.find()) {
            params.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        }
        return params.isEmpty() ? Map.of() : Map.copyOf(params);
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private record PendingAnnotation(String type, String value, Map<String, String> params, int offset) {}
}
