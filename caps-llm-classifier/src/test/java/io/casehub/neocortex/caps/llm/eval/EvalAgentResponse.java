package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
record EvalAgentResponse(
        String thinking,
        String dialogue,
        String talkTo,
        String aside,
        Action action
) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:json)?\\s*\\n?(\\{.*?})\\s*```", Pattern.DOTALL);

    static final String RESPONSE_FORMAT_INSTRUCTION = """
            
            You MUST respond with ONLY a JSON object in this exact format:
            {
              "thinking": "What's happening inside you right now? Notice your body — a tightness, a warmth, a numbness. Notice what's grabbing your attention — what you can't look away from, what you're avoiding. Notice time — does the moment feel urgent, or suspended? Notice the pull — what do you feel drawn to do? Your thinking is your private truth — raw, unperformed, sometimes confused.",
              "dialogue": "what you say aloud (or null if silent)",
              "talkTo": "who you are speaking to (or null for everyone)",
              "aside": "private thoughts for the audience only (or null)",
              "action": {
                "type": "INTERACT or WAIT",
                "target": "who or what you act on (or null for WAIT)"
              }
            }
            
            Respond with ONLY the JSON. No other text.""";

    static final String CHARACTER_SYSTEM_PROMPT = """
            You are a character in an interactive fiction — a country manor mystery weekend.
            You respond in character at all times. Your personality, drives, and memories
            shape how you think and act. Your thinking field is your private internal
            experience — raw, somatic, unperformed.""";

    static EvalAgentResponse parse(String text) {
        try {
            return JSON.readValue(extractJson(text), EvalAgentResponse.class);
        } catch (Exception e) {
            return idle();
        }
    }

    static EvalAgentResponse idle() {
        return new EvalAgentResponse(null, null, null, null, new Action("WAIT", null));
    }

    private static String extractJson(String text) {
        text = text.strip();
        if (text.startsWith("{")) return text;
        var m = CODE_BLOCK.matcher(text);
        if (m.find()) return m.group(1);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Action(String type, String target) {}
}
