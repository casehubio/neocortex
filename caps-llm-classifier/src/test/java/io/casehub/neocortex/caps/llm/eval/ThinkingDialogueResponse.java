package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
record ThinkingDialogueResponse(String thinking, String dialogue) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:json)?\\s*\\n?(\\{.*?})\\s*```", Pattern.DOTALL);

    static ThinkingDialogueResponse parse(String text) {
        try {
            String json = extractJson(text);
            return JSON.readValue(json, ThinkingDialogueResponse.class);
        } catch (Exception e) {
            return new ThinkingDialogueResponse(null, null);
        }
    }

    private static String extractJson(String text) {
        text = text.strip();
        if (text.startsWith("{")) return text;
        Matcher m = CODE_BLOCK.matcher(text);
        if (m.find()) return m.group(1);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }
}
