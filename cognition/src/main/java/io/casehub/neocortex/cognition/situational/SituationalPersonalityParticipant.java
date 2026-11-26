package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class SituationalPersonalityParticipant implements CognitionTickParticipant {



    private final PersonalityStateService stateService;
    private final SituationalDetector     detector;
    private final PersonalityStateIntegrator integrator;
    private final SituationalPersonalityConfig config;
    private final EffectiveCognitiveDefaults effectiveDefaults;
    private final AppraisalAccessor appraisalAccessor;
    private final MoodAccessor moodAccessor;
    private final CognitiveJournal journal;
    private final Clock clock;
    private final @org.jspecify.annotations.Nullable TrustAccessor trustAccessor;

    private final java.util.concurrent.ConcurrentHashMap<String, DetectionResult> lastResults = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.time.Instant> lastResultAccess = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile io.casehub.neocortex.mindmap.@org.jspecify.annotations.Nullable MindMapStore mindMapStore;


    public SituationalPersonalityParticipant(
            PersonalityStateService stateService,
            SituationalDetector detector,
            PersonalityStateIntegrator integrator,
            SituationalPersonalityConfig config,
            EffectiveCognitiveDefaults effectiveDefaults,
            AppraisalAccessor appraisalAccessor,
            MoodAccessor moodAccessor,
            CognitiveJournal journal,
            Clock clock) {
        this(stateService, detector, integrator, config, effectiveDefaults,
             appraisalAccessor, moodAccessor, null, journal, clock);
    }

    public SituationalPersonalityParticipant(
            PersonalityStateService stateService,
            SituationalDetector detector,
            PersonalityStateIntegrator integrator,
            SituationalPersonalityConfig config,
            EffectiveCognitiveDefaults effectiveDefaults,
            AppraisalAccessor appraisalAccessor,
            MoodAccessor moodAccessor,
            @org.jspecify.annotations.Nullable TrustAccessor trustAccessor,
            CognitiveJournal journal,
            Clock clock) {
        this.stateService = stateService;
        this.detector = detector;
        this.integrator = integrator;
        this.config = config;
        this.effectiveDefaults = effectiveDefaults;
        this.appraisalAccessor = appraisalAccessor;
        this.moodAccessor = moodAccessor;
        this.trustAccessor = trustAccessor;
        this.journal = journal;
        this.clock = clock;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var descriptor = context.descriptor();
        if (descriptor == null || descriptor.disposition() == null) return;

        var agentId = context.agentId();
        var tenantId = context.tenantId();
        var now = clock.instant();

        var evidence = buildEvidence(agentId, tenantId, context);
        var profiles = descriptor.disposition().situationalProfiles();
        var deltas = SituationalDeltaConverter.toSituationalDeltas(profiles);
        String provenance = profiles.isEmpty() ? "none" : profiles.getFirst().provenance();
        var agentKey = agentId + ":" + tenantId;
        var previousResult = lastResults.getOrDefault(agentKey, DetectionResult.initial());
        var phaseSalience = buildPhaseSalienceMap(agentId, tenantId, now, profiles);

        var state = stateService.transition(tenantId, agentId, now, st -> {
            var advanced = integrator.advance(st, now);
            double dtSeconds = Duration.between(st.cursorTime(), now).toMillis() / 1000.0;
            if (dtSeconds < 0) dtSeconds = 0;
            var detection = detector.detect(evidence, previousResult, dtSeconds);
            var withDetection = advanced.withDetection(detection);
            var compositeDelta = computeCompositeDelta(detection, deltas, phaseSalience);
            boolean hasWinner = !detection.winners().isEmpty();
            double maxIntensity = detection.intensities().values().stream()
                    .mapToDouble(Double::doubleValue).max().orElse(0);
            boolean evidenceSustained = hasWinner && maxIntensity > config.dynamics().epsilonPublish();
            if (!compositeDelta.isEmpty() && hasWinner) {
                return integrator.pull(withDetection, compositeDelta, maxIntensity, evidenceSustained, now, dtSeconds);
            }
            return withDetection;
        });

        var finalDetection = new DetectionResult(state.intensities(), state.engaged(), state.winners(),
                Map.of(), Map.of(), Map.of(), Map.of());
        lastResults.put(agentKey, finalDetection);
        lastResultAccess.put(agentKey, clock.instant());
        var compositeDelta = computeCompositeDelta(finalDetection, deltas, phaseSalience);

        journal.record(new CognitiveEntry.SituationalModulation(
                tenantId, agentId,
                context.tickEventRef() != null ? context.tickEventRef()
                        : io.casehub.neocortex.cognition.journal.EventRef.tick(0),
                now,
                toStringKeyMap(finalDetection.intensities()),
                winnerName(finalDetection, SituationalMode.Group.AFFECTIVE),
                winnerName(finalDetection, SituationalMode.Group.SOCIAL),
                compositeDelta,
                0,
                provenance));
    }

    public int evictIdle(java.time.Duration maxIdle) {
        var cutoff = clock.instant().minus(maxIdle);
        var stale = new java.util.ArrayList<String>();
        lastResultAccess.forEach((key, time) -> {
            if (time.isBefore(cutoff)) stale.add(key);
        });
        for (var key : stale) {
            lastResults.remove(key);
            lastResultAccess.remove(key);
        }
        return stale.size();
    }

    public void setMindMapStore(io.casehub.neocortex.mindmap.MindMapStore store) {
        this.mindMapStore = store;
    }


    public java.util.Optional<io.casehub.neocortex.cognition.trace.PipelineTrace.SituationalPersonalitySnapshot> currentSnapshot(String agentId, String tenantId) {
        var agentKey  = agentId + ":" + tenantId;
        var detection = lastResults.get(agentKey);
        if (detection == null) {return java.util.Optional.empty();}

        var modeIntensities = new LinkedHashMap<String, Double>();
        for (var entry : detection.intensities().entrySet()) {
            modeIntensities.put(entry.getKey().name(), entry.getValue());
        }

        var affWinner = detection.winner(SituationalMode.Group.AFFECTIVE);
        var socWinner = detection.winner(SituationalMode.Group.SOCIAL);

        var facetDisplacements = new LinkedHashMap<String, Double>();
        int publishedCount     = 0;
        var state              = stateService.currentState(tenantId, agentId);
        if (state != null) {
            for (var entry : state.facets().entrySet()) {
                var    fs         = entry.getValue();
                double totalShift = fs.homeOffset() + fs.displacement() + fs.promoted();
                facetDisplacements.put(entry.getKey(), totalShift);
                if (Math.abs(totalShift) >= PersonalityStateService.EPSILON_PUBLISH) {
                    publishedCount++;
                }
            }
        }

        return java.util.Optional.of(new io.casehub.neocortex.cognition.trace.PipelineTrace.SituationalPersonalitySnapshot(
                Map.copyOf(modeIntensities),
                affWinner != null ? affWinner.name() : null,
                socWinner != null ? socWinner.name() : null,
                Map.copyOf(facetDisplacements),
                publishedCount));
    }


    private SituationalEvidence buildEvidence(String agentId, String tenantId, CognitionTickContext context) {
        var builder = SituationalEvidence.builder();
        builder.situationInputs(appraisalAccessor.currentInputActivations(agentId, tenantId));
        builder.secDimensions(appraisalAccessor.currentSecDimensions(agentId, tenantId));
        builder.mood(
                moodAccessor.pleasure(agentId, tenantId),
                moodAccessor.arousal(agentId, tenantId),
                moodAccessor.dominance(agentId, tenantId));
        if (context.targetAgentId() != null) {
            double trust = trustAccessor != null
                    ? trustAccessor.trust(agentId, context.targetAgentId(), tenantId)
                    : 0.0;
            builder.interlocutor(trust);
        }
        return builder.build();
    }

    private java.util.Map<SituationalMode, Double> buildPhaseSalienceMap(
            String agentId, String tenantId, java.time.Instant now,
            java.util.List<io.casehub.eidos.api.SituationalProfile> profiles) {
        var store = this.mindMapStore;
        if (store == null) {return Map.of();}

        var personNodes = store.search(
                io.casehub.neocortex.mindmap.MindMapQuery.of(tenantId, 10)
                                                         .withType(io.casehub.neocortex.mindmap.SubgraphTypes.PERSON)
                                                         .withText(agentId));
        if (personNodes.isEmpty()) {return Map.of();}
        var personNode = personNodes.getFirst();

        String phase         = io.casehub.neocortex.mindmap.DevelopmentalPhaseResolver.resolvePhase(personNode, now);
        String primaryFamily = personNode.properties().get("archetype-1-family");

        var salienceMap = new java.util.EnumMap<SituationalMode, Double>(SituationalMode.class);
        for (var profile : profiles) {
            var mode = SituationalDeltaConverter.toMode(profile.mode());
            if (mode == null) {continue;}

            String family = resolveFamilyFromProvenance(profile.provenance(), primaryFamily);
            if (family == null) {continue;}

            int rank = resolveArchetypeRank(personNode, family);
            if (rank < 1) {continue;}

            String key = "archetype-" + rank + "-salience-" + phase;
            String val = personNode.properties().get(key);
            if (val != null) {
                try {
                    salienceMap.put(mode, Double.parseDouble(val));
                } catch (NumberFormatException ignored) {}
            }
        }
        return java.util.Map.copyOf(salienceMap);
    }

    private static String resolveFamilyFromProvenance(String provenance, String primaryFamily) {
        if (provenance == null) {return primaryFamily;}
        if (provenance.startsWith("family-default:")) {
            return provenance.substring("family-default:".length()).toLowerCase(java.util.Locale.ROOT);
        }
        return primaryFamily;
    }

    private static int resolveArchetypeRank(io.casehub.neocortex.mindmap.MindMapNode personNode, String family) {
        for (int rank = 1; rank <= 3; rank++) {
            String nodeFamily = personNode.properties().get("archetype-" + rank + "-family");
            if (family.equalsIgnoreCase(nodeFamily)) {return rank;}
        }
        return 0;
    }


    private Map<String, Double> computeCompositeDelta(DetectionResult detection,
                                                      Map<SituationalMode, Map<String, Double>> profileDeltas,
                                                      Map<SituationalMode, Double> phaseSalience) {
        var composite = new LinkedHashMap<String, Double>();
        for (var group : SituationalMode.Group.values()) {
            var winner = detection.winner(group);
            if (winner == null) {continue;}
            double intensity = detection.intensity(winner);
            double salience  = phaseSalience.getOrDefault(winner, 1.0);
            var    deltas    = profileDeltas.get(winner);
            if (deltas == null) {continue;}
            for (var entry : deltas.entrySet()) {
                composite.merge(entry.getKey(), entry.getValue() * intensity * salience, Double::sum);
            }
        }
        double sMax = config.dynamics().sMax();
        composite.replaceAll((k, v) -> Math.max(-sMax, Math.min(sMax, v)));
        return Map.copyOf(composite);
    }


    private static Map<String, Double> toStringKeyMap(Map<SituationalMode, Double> map) {
        var result = new LinkedHashMap<String, Double>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey().name(), entry.getValue());
        }
        return Map.copyOf(result);
    }

    private static String winnerName(DetectionResult detection, SituationalMode.Group group) {
        var w = detection.winner(group);
        return w != null ? w.name() : null;
    }

    private static String eventRefString(CognitionTickContext context) {
        return context.tickEventRef() != null ? context.tickEventRef().toString() : "tick";
    }

    public interface AppraisalAccessor {
        Map<String, Double> currentInputActivations(String agentId, String tenantId);
        Map<String, Double> currentSecDimensions(String agentId, String tenantId);
    }

    public interface MoodAccessor {
        double pleasure(String agentId, String tenantId);
        double arousal(String agentId, String tenantId);
        double dominance(String agentId, String tenantId);
    }

    public interface TrustAccessor {
        double trust(String observerAgentId, String targetAgentId, String tenantId);
    }

}
