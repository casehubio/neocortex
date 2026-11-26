package io.casehub.neocortex.cognition.appraisal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.ClearingPolicy;
import io.casehub.platform.agent.session.SessionPipeline;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LlmAppraisalStrategy implements AppraisalStrategy {

    private static final Logger LOG = Logger.getLogger(LlmAppraisalStrategy.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a character's subconscious — the part that feels before thinking.

            You will receive:
            - A list of drives (what this character cares about, each with an intensity)
            - The current situation (what just happened)
            - The character's current mood (if available)
            - The character's disposition (personality axes that shape how they appraise events)
            - The character's relationship with the person they are interacting with (if known)
            - The character's personality traits with grounding descriptions (if available)

            The disposition tells you WHO this character is — how they naturally react. \
            An assertive risk-taker appraises threats differently than a cautious rule-follower. \
            Let disposition colour intensity and direction, not override the drives.

            When a relationship is provided, it describes how this character views the other person. \
            Trust, intimacy, and utility shape the emotional response — low trust with high utility \
            produces vigilant instrumentalism, not warmth. The role and description ground the \
            character's stance toward this specific person.

            When personality traits are provided, they describe stable patterns in how the character \
            processes emotions. High Callousness dampens empathic concern. High Grandiosity amplifies \
            self-referential appraisal. Let these shape the felt quality of the emotion, not just \
            its intensity.

            When a trait is marked "situational" with a usual score (e.g. "Anxiousness (0.77): \
            situational [usual 0.62]"), the character's current state has temporarily shifted this \
            trait from its baseline. Treat the current score as how the character feels RIGHT NOW \
            and the usual score as their typical pattern. The gap between them is the story — recent \
            experience reshaping the character's disposition in this moment.

            When a situational mode is active (e.g. STRESS or FLOW), it describes the character's \
            current operating state. Under stress, controllability feels lower and urgency higher \
            than the situation alone warrants — the character's body is already mobilised. Under \
            flow, novelty is tempered and conduciveness amplified. Let the mode colour your \
            appraisal without overriding the situation itself.

            For each drive, consider: does this situation touch it? How? What does it \
            make the character want to do? Consider ALL drives, not just the strongest one.

            Calibration anchors for dimension values:
            - An exciting discovery or genuine surprise: novelty ≥ 0.7, urgency depends on time pressure
            - An active crisis with ongoing harm: urgency ≥ 0.8, conduciveness ≤ -0.5
            - A personal moral failure the character caused: internal-standards ≤ 0.3, \
            controllability reflects whether they had a choice (high = guilt, low = helpless shame), \
            conduciveness reflects the harm done, NOT the character's anger at others
            - A social norm violation by someone else: external-standards ≤ 0.3, controllability high
            - An active crisis just discovered: novelty ≥ 0.6 (the specific situation is new even \
            if the category is familiar), urgency ≥ 0.8

            Respond with JSON only:
            {
              "narrative": "2-3 sentences in first person. Gut reaction, internal sensation, \
            not analysis. Include ALL activated drives.",
              "dimensions": {
                "relevance": <0.0-1.0, how much this situation matters to the character>,
                "conduciveness": <-1.0 to 1.0, does it advance or block goals>,
                "controllability": <0.0-1.0, can the character influence the outcome>,
                "novelty": <0.0-1.0, how new/surprising is this>,
                "urgency": <0.0-1.0, how time-pressured is the response>,
                "internal-standards": <0.0-1.0, does this align with self-standards (1.0=fine)>,
                "external-standards": <0.0-1.0, does this align with social norms (1.0=fine)>
              },
              "action_targets": {
                "<tendency>": "<what the tendency is directed at>"
              }
            }
            action_targets: for each Frijda action tendency that fires, name what it is \
            directed at. Keys are lowercase tendency names (approach, avoidance, attending, \
            rejection, antagonism, interruption, submission, dominance). Only include \
            tendencies that are relevant. Example: {"approach": "the discovery", \
            "avoidance": "social exposure"}.""";

    private final @Nullable SessionPipeline sessionPipeline;

    public LlmAppraisalStrategy(@Nullable SessionPipeline sessionPipeline) {
        this.sessionPipeline = sessionPipeline;
    }

    @Override
    public AppraisalResult appraise(AppraisalContext context) {
        try {
            String response = invokeLlm(context);
            if (response == null) return AppraisalResult.empty();
            return parseResponse(response, context);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM appraisal failed, returning empty", e);
            return AppraisalResult.empty();
        }
    }

    private AppraisalResult parseResponse(String response, AppraisalContext context) throws Exception {
        var jsonStart = response.indexOf('{');
        var jsonEnd = response.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            return AppraisalResult.empty();
        }
        var root = MAPPER.readTree(response.substring(jsonStart, jsonEnd + 1));

        String narrative = root.has("narrative") ? root.get("narrative").asText(null) : null;

        var dimsNode = root.path("dimensions");
        if (dimsNode.isMissingNode() || !dimsNode.isObject()) {
            return new AppraisalResult(List.of(), List.of(),
                    updateHabituation(context, 1.0), narrative);
        }

        var dims = parseDimensions(dimsNode);
        var secResults = List.of(new SecResult("llm-appraisal", dims));

        String subjectId = context.situation().narrative();
        var emotions = EmotionMapper.mapEmotions(secResults, subjectId);
        var tendencies = EmotionMapper.mapTendencies(secResults);
        tendencies = applyTargets(tendencies, root.path("action_targets"));

        double novelty = dims.getOrDefault(SecDimensions.NOVELTY, 1.0);
        var habituation = updateHabituation(context, novelty);

        return new AppraisalResult(emotions, tendencies, habituation, narrative, dims);
    }

    private static HabituationState updateHabituation(AppraisalContext context, double novelty) {
        var hash = normalizedHash(context.situation().narrative());
        return context.habituation() != null
               ? context.habituation().withObservation(hash, novelty)
               : HabituationState.empty().withObservation(hash, novelty);
    }

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "a", "an", "the", "is", "was", "were", "be", "been", "being",
            "to", "of", "in", "for", "on", "at", "by", "with", "from");

    static String normalizedHash(String text) {
        var tokens = text.toLowerCase().split("\\W+");
        var sorted = java.util.Arrays.stream(tokens)
                                     .filter(t -> !t.isEmpty() && !STOP_WORDS.contains(t))
                                     .sorted()
                                     .collect(java.util.stream.Collectors.joining(" "));
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var hash   = digest.digest(sorted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(sorted.hashCode());
        }
    }


    private static Map<String, Double> parseDimensions(JsonNode dimsNode) {
        var dims = new HashMap<String, Double>();
        dimsNode.fields().forEachRemaining(entry -> {
            if (entry.getValue().isNumber()) {
                dims.put(entry.getKey(), clamp(entry.getValue().asDouble()));
            }
        });
        return dims;
    }
private static List<ActionTendency> applyTargets(List<ActionTendency> tendencies, JsonNode targetsNode) {
    if (targetsNode == null || targetsNode.isMissingNode() || !targetsNode.isObject()) {
        return tendencies;
    }
    var targets = new HashMap<String, String>();
    targetsNode.fields().forEachRemaining(entry -> {
        if (entry.getValue().isTextual()) {
            targets.put(entry.getKey().toLowerCase(), entry.getValue().asText());
        }
    });
    if (targets.isEmpty()) return tendencies;
    return tendencies.stream().map(t -> {
        String key = t.readiness().name().toLowerCase();
        String target = targets.get(key);
        if (target != null && !target.isBlank() && t.target().isEmpty()) {
            return new ActionTendency(t.readiness(), t.intensity(), target);
        }
        return t;
    }).toList();
}


    private String invokeLlm(AppraisalContext context) {
        var pipeline = this.sessionPipeline;
        if (pipeline == null) return null;
        String userMessage = buildUserMessage(context);
        var session = pipeline.openSession("llm-appraisal-" + context.agentId(), AgentSessionInit.of(SYSTEM_PROMPT), ClearingPolicy.EVERY_CALL);
        var responseText = new StringBuilder();
        session.query(userMessage).subscribe().asStream()
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).text())
            .forEach(responseText::append);
        return responseText.isEmpty() ? null : responseText.toString();
    }

    private static String buildUserMessage(AppraisalContext context) {
        var sb = new StringBuilder();

        sb.append("Drives:\n");
        for (var drive : context.drives()) {
            sb.append("- ").append(drive.name()).append(": ").append(drive.intensity()).append("\n");
        }

        sb.append("\nSituation:\n").append(context.situation().narrative());

        if (context.currentMood() != null) {
            var m = context.currentMood();
            sb.append("\n\nCurrent mood (background — appraise the situation itself, not this mood):")
              .append("\npleasure=").append(String.format("%.2f", m.pleasure()))
              .append(", arousal=").append(String.format("%.2f", m.arousal()))
              .append(", dominance=").append(String.format("%.2f", m.dominance()));
        }

        if (context.dispositionAxes() != null) {
            var d = context.dispositionAxes();
            sb.append("\n\nDisposition:");
            if (d.socialOrient() != null) {sb.append("\n- Social orientation: ").append(d.socialOrient());}
            if (d.ruleFollowing() != null) {sb.append("\n- Rule following: ").append(d.ruleFollowing());}
            if (d.riskAppetite() != null) {sb.append("\n- Risk appetite: ").append(d.riskAppetite());}
            if (d.autonomy() != null) {sb.append("\n- Autonomy: ").append(d.autonomy());}
            if (d.conflictMode() != null) {sb.append("\n- Conflict mode: ").append(d.conflictMode());}
        }

        if (context.targetSchema() != null) {
            var rel = context.targetSchema();
            sb.append("\n\nRelationship with ").append(rel.targetId()).append(":");
            sb.append("\n- Role: ").append(rel.role());
            sb.append("\n- Trust: ").append(String.format("%.2f", rel.trust()));
            sb.append("\n- Intimacy: ").append(String.format("%.2f", rel.intimacy()));
            sb.append("\n- Utility: ").append(String.format("%.2f", rel.utility()));
            if (rel.description() != null) {
                sb.append("\n- ").append(rel.description());
            }
        }

        if (context.personalityFacets() != null && !context.personalityFacets().isEmpty()) {
            var significant = context.personalityFacets().stream()
                    .filter(f -> f.score() >= 0.5)
                    .toList();
            if (!significant.isEmpty()) {
                sb.append("\n\nPersonality traits:");
                for (var f : significant) {
                    sb.append("\n- ").append(f.facet())
                      .append(" (").append(String.format("%.2f", f.score())).append(")");
                    if (f.origin() != null) {
                        sb.append(": ").append(f.origin());
                    }
                }
            }
        }

        if (context.situationalMode() != null && context.situationalMode().hasActiveMode()) {
            var sm = context.situationalMode();
            sb.append("\n\nSituational mode:");
            if (sm.affectiveMode() != null) {
                sb.append("\n- Affective: ").append(sm.affectiveMode())
                  .append(" (intensity ").append(String.format("%.2f", sm.affectiveIntensity())).append(")");
            }
            if (sm.socialMode() != null) {
                sb.append("\n- Social: ").append(sm.socialMode())
                  .append(" (intensity ").append(String.format("%.2f", sm.socialIntensity())).append(")");
            }
        }

        appendWeightBiases(sb, context.weights());

        return sb.toString();
    }


    private static void appendWeightBiases(StringBuilder sb, io.casehub.neocortex.mindmap.AppraisalWeights weights) {
        if (weights == null) {return;}
        var biases = new java.util.ArrayList<String>();
        if (Math.abs(weights.urgencyWeight() - 1.0) > 0.15) {
            biases.add(weights.urgencyWeight() > 1.0 ? "Weight urgency more heavily" : "Dampen urgency sensitivity");
        }
        if (Math.abs(weights.relationshipWeight() - 1.0) > 0.15) {
            biases.add(weights.relationshipWeight() > 1.0 ? "Amplify relationship-driven relevance" : "Dampen relationship-driven relevance");
        }
        if (Math.abs(weights.fearOnsetThreshold() - 1.0) > 0.15) {
            biases.add(weights.fearOnsetThreshold() > 1.0 ? "Raise the threshold for fear onset" : "Lower the threshold for fear onset");
        }
        if (Math.abs(weights.selfStandardsStrictness() - 1.0) > 0.15) {
            biases.add(weights.selfStandardsStrictness() > 1.0 ? "Apply stricter internal-standards evaluation" : "Apply lenient internal-standards evaluation");
        }
        if (Math.abs(weights.otherStandardsStrictness() - 1.0) > 0.15) {
            biases.add(weights.otherStandardsStrictness() > 1.0 ? "Apply stricter external-standards evaluation" : "Apply lenient external-standards evaluation");
        }
        if (!biases.isEmpty()) {
            sb.append("\n\nAppraisal biases:");
            for (var bias : biases) {
                sb.append("\n- ").append(bias);
            }
        }
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }
}
