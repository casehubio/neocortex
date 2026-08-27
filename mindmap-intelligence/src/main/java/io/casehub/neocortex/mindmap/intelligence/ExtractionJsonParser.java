package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.ConfidenceOrigin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

final class ExtractionJsonParser {

    private static final Logger LOG = Logger.getLogger(ExtractionJsonParser.class.getName());

    private ExtractionJsonParser() {}

    static ParsedExtraction parse(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String json = stripMarkdownWrapper(raw);
        json = extractFirstJsonObject(json);
        if (json == null) return null;

        try {
            return parseJsonObject(json);
        } catch (Exception e) {
            LOG.warning("Failed to parse extraction JSON: " + e.getMessage());
            return null;
        }
    }

    private static String stripMarkdownWrapper(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            return trimmed.strip();
        }
        return trimmed;
    }

    private static String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return null;
    }

    private static ParsedExtraction parseJsonObject(String json) {
        List<ParsedEntity> entities = new ArrayList<>();
        List<ParsedRelationship> relationships = new ArrayList<>();
        List<ParsedContradiction> contradictions = new ArrayList<>();

        String entitiesJson = extractArray(json, "entities");
        if (entitiesJson != null) {
            for (String obj : splitArrayObjects(entitiesJson)) {
                entities.add(parseEntity(obj));
            }
        }

        String relsJson = extractArray(json, "relationships");
        if (relsJson != null) {
            for (String obj : splitArrayObjects(relsJson)) {
                relationships.add(parseRelationship(obj));
            }
        }

        String contradictionsJson = extractArray(json, "contradictions");
        if (contradictionsJson != null) {
            for (String obj : splitArrayObjects(contradictionsJson)) {
                contradictions.add(parseContradiction(obj));
            }
        }

        return new ParsedExtraction(entities, relationships, contradictions);
    }

    private static ParsedEntity parseEntity(String obj) {
        String name = extractString(obj, "name");
        String type = extractString(obj, "type");
        ConfidenceOrigin confidence = parseConfidence(extractString(obj, "confidence"));
        Map<String, String> props = extractStringMap(obj, "properties");
        return new ParsedEntity(name, type, props, confidence);
    }

    private static ParsedRelationship parseRelationship(String obj) {
        return new ParsedRelationship(
            extractString(obj, "source"),
            extractString(obj, "target"),
            extractString(obj, "type"),
            parseConfidence(extractString(obj, "confidence")));
    }

    private static ParsedContradiction parseContradiction(String obj) {
        return new ParsedContradiction(
            extractString(obj, "entity"),
            extractString(obj, "property"),
            extractString(obj, "existing"),
            extractString(obj, "extracted"),
            extractString(obj, "explanation"));
    }

    private static ConfidenceOrigin parseConfidence(String value) {
        if (value == null) return ConfidenceOrigin.INFERRED;
        try {
            return ConfidenceOrigin.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ConfidenceOrigin.INFERRED;
        }
    }

    static String extractArray(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return null;
        int bracketIdx = json.indexOf('[', colonIdx);
        if (bracketIdx < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = bracketIdx; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(bracketIdx + 1, i);
            }
        }
        return null;
    }

    static List<String> splitArrayObjects(String arrayContent) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(arrayContent.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    static String extractString(String obj, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = obj.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = obj.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return null;
        int i = colonIdx + 1;
        while (i < obj.length() && (obj.charAt(i) == ' ' || obj.charAt(i) == '\n' || obj.charAt(i) == '\r' || obj.charAt(i) == '\t')) i++;
        if (i >= obj.length()) return null;
        char c = obj.charAt(i);
        if (c == '"') {
            int end = findClosingQuote(obj, i + 1);
            if (end < 0) return null;
            return unescape(obj.substring(i + 1, end));
        }
        if (c == '{' || c == '[' || c == 'n') return null;
        int end = i;
        while (end < obj.length() && obj.charAt(end) != ',' && obj.charAt(end) != '}') end++;
        return obj.substring(i, end).strip();
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"', '\\', '/' -> { sb.append(next); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int findClosingQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '\\') { i++; continue; }
            if (s.charAt(i) == '"') return i;
        }
        return -1;
    }

    static Map<String, String> extractStringMap(String obj, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = obj.indexOf(search);
        if (keyIdx < 0) return Map.of();
        int colonIdx = obj.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return Map.of();
        int braceIdx = obj.indexOf('{', colonIdx);
        if (braceIdx < 0) return Map.of();
        int depth = 0;
        int end = -1;
        boolean inStr = false;
        boolean esc = false;
        for (int i = braceIdx; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { end = i; break; }
            }
        }
        if (end < 0) return Map.of();
        String inner = obj.substring(braceIdx + 1, end).strip();
        if (inner.isEmpty()) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        int pos = 0;
        while (pos < inner.length()) {
            int qStart = inner.indexOf('"', pos);
            if (qStart < 0) break;
            int qEnd = findClosingQuote(inner, qStart + 1);
            if (qEnd < 0) break;
            String k = unescape(inner.substring(qStart + 1, qEnd));
            int c = inner.indexOf(':', qEnd + 1);
            if (c < 0) break;
            int vStart = c + 1;
            while (vStart < inner.length() && Character.isWhitespace(inner.charAt(vStart))) vStart++;
            if (vStart >= inner.length()) break;
            if (inner.charAt(vStart) == '"') {
                int vEnd = findClosingQuote(inner, vStart + 1);
                if (vEnd < 0) break;
                map.put(k, unescape(inner.substring(vStart + 1, vEnd)));
                pos = vEnd + 1;
            } else {
                int vEnd = vStart;
                while (vEnd < inner.length() && inner.charAt(vEnd) != ',' && inner.charAt(vEnd) != '}') vEnd++;
                map.put(k, inner.substring(vStart, vEnd).strip());
                pos = vEnd + 1;
            }
        }
        return map;
    }
}
