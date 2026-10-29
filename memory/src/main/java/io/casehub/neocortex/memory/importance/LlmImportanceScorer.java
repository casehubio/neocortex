package io.casehub.neocortex.memory.importance;

import io.casehub.neocortex.memory.ImportanceScorer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LlmImportanceScorer implements ImportanceScorer {

    private static final Logger LOG = Logger.getLogger(LlmImportanceScorer.class.getName());
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
        "\"index\"\\s*:\\s*(\\d+)\\s*,\\s*\"score\"\\s*:\\s*(-?\\d+)");

    private final Function<String, String> llmCall;

    public LlmImportanceScorer(Function<String, String> llmCall) {
        this.llmCall = llmCall;
    }

    @Override
    public Map<String, Integer> score(List<ScoringEntry> batch) {
        if (batch.isEmpty()) return Map.of();

        String prompt = buildPrompt(batch);
        String response;
        try {
            response = llmCall.apply(prompt);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM importance scoring call failed", e);
            return Map.of();
        }

        return parseResponse(response, batch);
    }

    private String buildPrompt(List<ScoringEntry> batch) {
        var sb = new StringBuilder();
        sb.append("Rate the importance of each memory on a scale of 1-10 ");
        sb.append("(1=trivial, 10=critical). Respond with a JSON array of ");
        sb.append("{\"index\": N, \"score\": S} objects.\n\n");
        for (int i = 0; i < batch.size(); i++) {
            sb.append(i).append(". [").append(batch.get(i).domain()).append("] ")
              .append(batch.get(i).text()).append("\n");
        }
        return sb.toString();
    }

    private Map<String, Integer> parseResponse(String response, List<ScoringEntry> batch) {
        Map<String, Integer> result = new HashMap<>();
        Matcher m = ENTRY_PATTERN.matcher(response);
        while (m.find()) {
            try {
                int index = Integer.parseInt(m.group(1));
                int score = Integer.parseInt(m.group(2));
                score = Math.max(1, Math.min(10, score));
                if (index >= 0 && index < batch.size()) {
                    result.put(batch.get(index).memoryId(), score);
                }
            } catch (NumberFormatException e) {
                // skip malformed entry
            }
        }
        return result;
    }
}
