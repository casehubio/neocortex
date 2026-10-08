package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.memory.experience.SubThoughtTypes;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ApplicationScoped
public class RuleBasedSubThoughtExtractor {

    private static final Map<String, List<String>> KEYWORD_SETS = Map.of(
        SubThoughtTypes.AFFECT_OBSERVATION, List.of("felt", "seemed", "appeared", "looked", "sounded", "happy", "sad", "anxious", "distressed", "upset", "worried", "cheerful", "tense", "relaxed", "frustrated"),
        SubThoughtTypes.CAUSAL_INFERENCE, List.of("because", "since", "caused", "due to", "reason", "therefore", "so that", "resulted in", "led to", "as a result", "explains why"),
        SubThoughtTypes.INTENTION, List.of("should", "plan to", "going to", "need to", "want to", "intend", "must", "ought to", "let's", "i'll"),
        SubThoughtTypes.SELF_REFLECTION, List.of("i feel", "i think", "i wonder", "i notice", "i realize", "it occurs to me", "looking back", "on reflection"),
        SubThoughtTypes.EVALUATIVE, List.of("good", "bad", "excellent", "terrible", "impressive", "disappointing", "wonderful", "awful", "great", "poor", "amazing", "mediocre"),
        SubThoughtTypes.ASSOCIATION, List.of("reminds me", "similar to", "like when", "just like", "connects to", "makes me think of", "brings to mind"),
        SubThoughtTypes.CONCERN, List.of("worry", "concerned", "afraid", "fear", "anxious about", "troubled by", "uneasy", "dread", "scared")
    );

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    private final ConcurrentHashMap<String, Set<String>> entityCache = new ConcurrentHashMap<>();

    public List<SubThought> extract(String observation, String agentId, String tenantId) {
        if (observation == null || observation.isBlank()) return List.of();

        String[] sentences = SENTENCE_SPLIT.split(observation);
        var result = new ArrayList<SubThought>();
        Set<String> entities = entityCache.get(tenantId);

        for (String sentence : sentences) {
            String trimmed = sentence.strip();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase(Locale.ROOT);

            String bestType = null;
            int bestCount = 0;
            for (var entry : KEYWORD_SETS.entrySet()) {
                int count = countMatches(lower, entry.getValue());
                if (count > bestCount) {
                    bestCount = count;
                    bestType = entry.getKey();
                }
            }

            if (bestType != null) {
                String entity = findEntity(trimmed, entities);
                result.add(new SubThought(bestType, trimmed, entity, 0.5, SubThought.Source.SYNC));
            }
        }
        return List.copyOf(result);
    }

    public void refreshEntityCache(String tenantId, Set<String> names) {
        entityCache.put(tenantId, Set.copyOf(names));
    }

    private int countMatches(String lower, List<String> keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (containsWord(lower, kw)) count++;
        }
        return count;
    }

    private boolean containsWord(String text, String keyword) {
        int idx = text.indexOf(keyword);
        if (idx < 0) return false;
        boolean startOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
        int end = idx + keyword.length();
        boolean endOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return startOk && endOk;
    }

    private String findEntity(String sentence, Set<String> entities) {
        if (entities == null || entities.isEmpty()) return null;
        String lower = sentence.toLowerCase(Locale.ROOT);
        for (String entity : entities) {
            if (containsWord(lower, entity.toLowerCase(Locale.ROOT))) {
                return entity;
            }
        }
        return null;
    }
}
