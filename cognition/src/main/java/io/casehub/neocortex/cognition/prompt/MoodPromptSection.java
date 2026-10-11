package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.memory.mood.MoodState;

public class MoodPromptSection implements CognitionPromptRenderer {

    private final                                    MoodOrchestrator      mood;
    private final @org.jspecify.annotations.Nullable PromptRenderingConfig config;

    public MoodPromptSection(MoodOrchestrator mood) {
        this(mood, null);
    }

    public MoodPromptSection(MoodOrchestrator mood,
                             @org.jspecify.annotations.Nullable PromptRenderingConfig config) {
        this.mood   = mood;
        this.config = config;
    }

    private double padThreshold() {return config != null ? config.moodPadThreshold() : 0.3;}

    @Override
    public @org.jspecify.annotations.Nullable String render(CognitionRenderContext context) {
        return mood.currentMood(context.agentId(), context.tenantId())
                   .map(state -> renderMood(state, padThreshold()))
                   .orElse(null);
    }

    private static String renderMood(MoodState state, double threshold) {
        var label    = emotionLabel(state.pleasure(), state.arousal(), state.dominance(), threshold);
        var coloring = behavioralColoring(state.pleasure(), state.arousal(), state.dominance(), threshold);
        return "Current emotional state:\nYou're feeling " + label + ".\n" + coloring;
    }

    public static String emotionLabel(double p, double a, double d) {
        return emotionLabel(p, a, d, 0.3);
    }

    public static String emotionLabel(double p, double a, double d, double threshold) {
        String primary;
        if (p > threshold && a > threshold) {
            primary = "excited and energized";
        } else if (p > threshold && a < -threshold) {
            primary = "content and serene";
        } else if (p > threshold) {
            primary = "pleased";
        } else if (p < -threshold && a > threshold) {
            primary = "tense and agitated";
        } else if (p < -threshold && a < -threshold) {
            primary = "subdued and low";
        } else if (p < -threshold) {
            primary = "displeased";
        } else if (a > threshold) {
            primary = "alert and keyed up";
        } else if (a < -threshold) {primary = "calm and unhurried";} else {
            primary = "emotionally even";
        }

        if (d > threshold) {return primary + ", with a sense of confidence";}
        if (d < -threshold) {return primary + ", with an undercurrent of uncertainty";}
        return primary;
    }

    public static String behavioralColoring(double p, double a, double d) {
        return behavioralColoring(p, a, d, 0.3);
    }

    public static String behavioralColoring(double p, double a, double d, double threshold) {
        var cues = new java.util.ArrayList<String>();
        if (p > threshold) {cues.add("more generous and open");}
        if (p < -threshold) {cues.add("more guarded and short-tempered");}
        if (a > threshold) {cues.add("quicker to react and more impulsive");}
        if (a < -threshold) {cues.add("more measured and deliberate");}
        if (d > threshold) {cues.add("more assertive and commanding");}
        if (d < -threshold) {cues.add("more hesitant and deferential");}
        if (cues.isEmpty()) {return "This doesn't noticeably color your behavior.";}
        return "This colors your responses — you're " + String.join(", ", cues) + ".";
    }
}
