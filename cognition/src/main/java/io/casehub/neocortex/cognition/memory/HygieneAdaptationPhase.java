package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.KnowledgeGapSummary;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.RetrievalMode;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import io.casehub.platform.api.path.Path;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;

import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(48)
@IfBuildProperty(name = "casehub.cognition.hygiene-adaptation.enabled",
                 stringValue = "true", enableIfMissing = false)
public class HygieneAdaptationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(HygieneAdaptationPhase.class.getName());
    static final String CASE_TYPE = "retention-config";
    static final MemoryDomain DOMAIN = new MemoryDomain("hygiene");

    private final MemoryHygieneOrchestrator hygieneOrchestrator;
    private final AdaptiveRetentionConfig adaptiveConfig;
    private final HygieneAdaptationConfig config;
    private final Consumer<HygieneAdaptationEvent> eventSink;
    private final CbrRecordStore cbrRecordStore;

    private final ConcurrentHashMap<String, KnowledgeGapSummary> previousSummaries =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RetentionConfig> previousConfigs =
            new ConcurrentHashMap<>();
    private final Set<String> activeTenants =
            ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> stabilityCounters =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeCaseIds =
            new ConcurrentHashMap<>();

    public HygieneAdaptationPhase(
            MemoryHygieneOrchestrator hygieneOrchestrator,
            AdaptiveRetentionConfig adaptiveConfig,
            HygieneAdaptationConfig config,
            Event<HygieneAdaptationEvent> adaptationEvent,
            Instance<CbrRecordStore> cbrRecordStoreInstance) {
        this(hygieneOrchestrator, adaptiveConfig, config,
             (Consumer<HygieneAdaptationEvent>) adaptationEvent::fire,
             cbrRecordStoreInstance.isResolvable() ? cbrRecordStoreInstance.get() : null);
    }

    HygieneAdaptationPhase(
            MemoryHygieneOrchestrator hygieneOrchestrator,
            AdaptiveRetentionConfig adaptiveConfig,
            HygieneAdaptationConfig config,
            Consumer<HygieneAdaptationEvent> eventSink) {
        this(hygieneOrchestrator, adaptiveConfig, config, eventSink, null);
    }

    HygieneAdaptationPhase(
            MemoryHygieneOrchestrator hygieneOrchestrator,
            AdaptiveRetentionConfig adaptiveConfig,
            HygieneAdaptationConfig config,
            Consumer<HygieneAdaptationEvent> eventSink,
            CbrRecordStore cbrRecordStore) {
        this.hygieneOrchestrator = hygieneOrchestrator;
        this.adaptiveConfig = adaptiveConfig;
        this.config = config;
        this.eventSink = eventSink;
        this.cbrRecordStore = cbrRecordStore;
        registerSchema();
    }

    @Override
    public String name() {
        return "hygiene-adaptation";
    }

    @Override
    public void beginTick() {
        if (!activeTenants.isEmpty()) {
            adaptiveConfig.evictAbsent(activeTenants);
            previousSummaries.keySet().retainAll(activeTenants);
            previousConfigs.keySet().retainAll(activeTenants);
            stabilityCounters.keySet().retainAll(activeTenants);
            activeCaseIds.keySet().retainAll(activeTenants);
        }
        activeTenants.clear();
    }


    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        activeTenants.add(tenantId);
        var currentSummary = hygieneOrchestrator.tenantGapSummary(tenantId);
        if (currentSummary.totalScored() == 0) {return;}

        var previous = previousSummaries.get(tenantId);
        var currentConfig = adaptiveConfig.current(tenantId);

        if (previous == null) {
            retrievePriorConfig(tenantId, currentSummary);
            previousSummaries.put(tenantId, currentSummary);
            previousConfigs.put(tenantId, adaptiveConfig.current(tenantId));
            return;
        }

        double pressure = (double) currentSummary.lowRetentionCount() / currentSummary.totalScored();
        double previousPressure = previous.totalScored() > 0
                                  ? (double) previous.lowRetentionCount() / previous.totalScored()
                                  : 0.0;

        var prevConfig = previousConfigs.getOrDefault(tenantId, currentConfig);
        boolean previouslyAdjusted =
                prevConfig.retentionThreshold() != currentConfig.retentionThreshold();

        if (previouslyAdjusted) {
            boolean improving =
                    Math.abs(pressure - targetMidpoint()) < Math.abs(previousPressure - targetMidpoint());
            if (improving) {
                previousSummaries.put(tenantId, currentSummary);
                previousConfigs.put(tenantId, currentConfig);
                return;
            }
        }

        double newThreshold = currentConfig.retentionThreshold();
        HygieneAdaptationEvent.AdaptationDirection direction;

        if (pressure > config.evictionPressureUpperThreshold()) {
            newThreshold -= config.stepSize();
            direction = HygieneAdaptationEvent.AdaptationDirection.LOWERED;
        } else if (pressure < config.evictionPressureLowerThreshold()) {
            newThreshold += config.stepSize();
            direction = HygieneAdaptationEvent.AdaptationDirection.RAISED;
        } else {
            int counter = stabilityCounters.merge(tenantId, 1, Integer::sum);
            if (counter >= config.stabilityThreshold()) {
                double stablePressure = (double) currentSummary.lowRetentionCount() / currentSummary.totalScored();
                storeStableConfig(tenantId, currentConfig, stablePressure,
                        currentSummary.totalScored(), currentSummary.consolidationGroups());
            }
            previousSummaries.put(tenantId, currentSummary);
            previousConfigs.put(tenantId, currentConfig);
            return;
        }

        newThreshold = Math.max(config.minRetentionThreshold(),
                                Math.min(config.maxRetentionThreshold(), newThreshold));

        if (newThreshold == currentConfig.retentionThreshold()) {
            previousSummaries.put(tenantId, currentSummary);
            previousConfigs.put(tenantId, currentConfig);
            return;
        }

        var newConfig = new RetentionConfig(newThreshold,
                                            currentConfig.recencyWeight(), currentConfig.frequencyWeight());

        adaptiveConfig.update(tenantId, newConfig);

        eventSink.accept(new HygieneAdaptationEvent(
                tenantId, currentConfig, newConfig, pressure, direction, Instant.now()));

        stabilityCounters.put(tenantId, 0);
        previousSummaries.put(tenantId, currentSummary);
        previousConfigs.put(tenantId, newConfig);
    }

    private double targetMidpoint() {
        return (config.evictionPressureUpperThreshold() + config.evictionPressureLowerThreshold()) / 2.0;
    }

    private void registerSchema() {
        if (cbrRecordStore == null) return;
        try {
            cbrRecordStore.registerSchema(CbrRecordSchema.of(CASE_TYPE,
                    new FeatureField.Numeric("evictionPressure", 0.0, 1.0),
                    new FeatureField.Numeric("totalScored", 0, 100_000),
                    new FeatureField.Numeric("consolidationGroups", 0, 10_000)));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to register retention-config CBR schema", e);
        }
    }

    private void storeStableConfig(String tenantId, RetentionConfig retentionConfig,
                                    double pressure, int totalScored, int consolidationGroups) {
        if (cbrRecordStore == null) return;

        var features = Map.<String, FeatureValue>of(
                "evictionPressure", FeatureValue.number(pressure),
                "totalScored", FeatureValue.number(totalScored),
                "consolidationGroups", FeatureValue.number(consolidationGroups));

        var record = new CbrFeatureRecord(
                "retention threshold " + retentionConfig.retentionThreshold()
                        + " at pressure " + String.format("%.3f", pressure)
                        + " for " + totalScored + " memories",
                "retentionThreshold=" + retentionConfig.retentionThreshold(),
                null,
                new Confidence(ConfidenceOrigin.INFERRED, 1.0, null),
                features, null, null);

        try {
            String newCaseId = cbrRecordStore.store(record, CASE_TYPE, tenantId,
                    DOMAIN, tenantId, null, Path.root());

            String previousCaseId = activeCaseIds.put(tenantId, newCaseId);
            if (previousCaseId != null) {
                try {
                    cbrRecordStore.supersede(previousCaseId, newCaseId,
                            "newer stable config", tenantId);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to supersede previous retention case", e);
                }
            }

            try {
                cbrRecordStore.recordOutcome(newCaseId,
                        CbrOutcome.of(1.0, "stable for " + config.stabilityThreshold() + " ticks",
                                Instant.now()),
                        tenantId);
            } catch (Exception e) {
                LOG.log(Level.FINE, "Failed to record outcome for retention case", e);
            }

            stabilityCounters.put(tenantId, 0);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to store retention config CBR case for tenant " + tenantId, e);
        }
    }

    private void retrievePriorConfig(String tenantId, KnowledgeGapSummary currentSummary) {
        if (cbrRecordStore == null) return;
        if (currentSummary.totalScored() == 0) return;

        double pressure = (double) currentSummary.lowRetentionCount() / currentSummary.totalScored();

        try {
            var query = CbrQuery.of(tenantId, DOMAIN, Path.root(),
                            CASE_TYPE,
                            Map.of("evictionPressure", FeatureValue.number(pressure),
                                   "totalScored", FeatureValue.number(currentSummary.totalScored()),
                                   "consolidationGroups", FeatureValue.number(currentSummary.consolidationGroups())),
                            1)
                    .withMinSimilarity(config.cbrMinSimilarity())
                    .withRetrievalMode(RetrievalMode.FEATURE_ONLY);

            var matches = cbrRecordStore.retrieveSimilar(query, CbrRecord.class);
            if (!matches.isEmpty()) {
                var best = matches.getFirst();
                String solution = best.cbrRecord().solution();
                if (solution != null && solution.startsWith("retentionThreshold=")) {
                    double threshold = Double.parseDouble(
                            solution.substring("retentionThreshold=".length()));
                    var currentConfig = adaptiveConfig.current(tenantId);
                    var restoredConfig = new RetentionConfig(threshold,
                            currentConfig.recencyWeight(), currentConfig.frequencyWeight());
                    adaptiveConfig.update(tenantId, restoredConfig);
                    activeCaseIds.put(tenantId, best.caseId());
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "CBR retrieval failed for tenant " + tenantId, e);
        }
    }
}
