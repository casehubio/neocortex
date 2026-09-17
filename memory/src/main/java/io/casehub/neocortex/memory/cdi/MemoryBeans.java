package io.casehub.neocortex.memory.cdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.cbr.CbrEventTypes;
import io.casehub.neocortex.memory.cbr.CbrOutcomeData;
import io.casehub.memory.runtime.CaseEnrichmentPipeline;
import io.casehub.memory.runtime.MemoryEmitterCore;
import io.casehub.neocortex.memory.CaseEnrichmentStep;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.ExplanationRenderer;
import io.casehub.neocortex.memory.cbr.OutcomeWeightingFunction;
import io.casehub.neocortex.memory.cbr.TrustWeightingFunction;
import io.casehub.neocortex.memory.cbr.runtime.CbrOutcomeProcessor;
import io.casehub.neocortex.memory.cbr.runtime.CbrRetentionConfig;
import io.casehub.neocortex.memory.cbr.runtime.CbrRetentionPurger;
import io.casehub.neocortex.memory.cbr.runtime.DefaultExplanationRenderer;
import io.casehub.neocortex.memory.cbr.runtime.DefaultOutcomeWeightingFunction;
import io.casehub.neocortex.memory.cbr.runtime.DefaultTrustWeightingFunction;
import io.casehub.neocortex.memory.cbr.runtime.OutcomeWeightingConfig;
import io.casehub.neocortex.memory.cbr.runtime.TrustRetentionConfig;
import io.casehub.neocortex.memory.cbr.runtime.TrustRetentionPurger;
import io.casehub.neocortex.memory.cbr.runtime.TrustWeightingConfig;
import io.casehub.neocortex.memory.engagement.EngagementRecorded;
import io.casehub.neocortex.memory.engagement.runtime.EngagementRecorderCore;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.runtime.ExperienceRecorderCore;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.neocortex.memory.reflection.ReflectionRecorded;
import io.casehub.neocortex.memory.reflection.ReflectionSynthesizer;
import io.casehub.neocortex.memory.reflection.runtime.ReflectionOrchestratorCore;
import io.casehub.neocortex.memory.relationship.RelationshipRecorded;
import io.casehub.neocortex.memory.relationship.runtime.RelationshipProcessor;
import io.casehub.neocortex.memory.runtime.MemoryRetentionConfig;
import io.casehub.neocortex.memory.runtime.MemoryRetentionPurger;
import io.casehub.platform.api.event.CloudEventType;
import io.cloudevents.CloudEvent;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Comparator;

@ApplicationScoped
public class MemoryBeans {

    private static final Logger LOG = Logger.getLogger(MemoryBeans.class);

    // --- Default implementations ---

    @Produces
    @DefaultBean
    @ApplicationScoped
    public TrustWeightingFunction trustWeightingFunction(TrustWeightingConfig config) {
        return new DefaultTrustWeightingFunction(config.influence(), config.trajectorySensitivity());
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public OutcomeWeightingFunction outcomeWeightingFunction(OutcomeWeightingConfig config) {
        return new DefaultOutcomeWeightingFunction(config.influence());
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public ExplanationRenderer explanationRenderer() {
        return new DefaultExplanationRenderer();
    }

    // --- Services ---

    @Produces
    @ApplicationScoped
    public ExperienceRecorder experienceRecorder(CaseMemoryStore store,
                                                  Event<ExperienceRecorded> recorded) {
        return new ExperienceRecorderCore(store, recorded::fire);
    }

    @Produces
    @ApplicationScoped
    public EngagementRecorderCore engagementRecorder(CaseMemoryStore store,
                                                      Event<EngagementRecorded> recorded) {
        return new EngagementRecorderCore(store, recorded::fire);
    }

    @Produces
    @ApplicationScoped
    public ReflectionOrchestrator reflectionOrchestrator(CaseMemoryStore store,
                                                         ReflectionSynthesizer synthesizer,
                                                         Event<ReflectionRecorded> recorded) {
        return new ReflectionOrchestratorCore(store, synthesizer, recorded::fire);
    }

    @Produces
    @ApplicationScoped
    public RelationshipProcessor relationshipProcessor(CaseMemoryStore store,
                                                        Event<RelationshipRecorded> recorded) {
        return new RelationshipProcessor(store, recorded::fire);
    }

    @Produces
    @ApplicationScoped
    public MemoryEmitterCore memoryEmitter(CaseMemoryStore store) {
        return new MemoryEmitterCore(store);
    }

    // --- Retention ---

    @Produces
    @ApplicationScoped
    public MemoryRetentionPurger memoryRetentionPurger(CaseMemoryStore store) {
        return new MemoryRetentionPurger(store);
    }

    @Produces
    @ApplicationScoped
    public CbrRetentionPurger cbrRetentionPurger(CbrCaseMemoryStore store) {
        return new CbrRetentionPurger(store);
    }

    @Produces
    @ApplicationScoped
    public TrustRetentionPurger trustRetentionPurger(CbrCaseMemoryStore store,
                                                      Instance<AgentTrustProvider> trustProviderInstance) {
        AgentTrustProvider trustProvider = trustProviderInstance.isResolvable()
                ? trustProviderInstance.get() : null;
        return new TrustRetentionPurger(store, trustProvider);
    }

    @Produces
    @ApplicationScoped
    public CbrOutcomeProcessor cbrOutcomeProcessor(CbrCaseMemoryStore store) {
        return new CbrOutcomeProcessor(store);
    }

    // CaseEnrichmentPipeline is exposed via @Decorator CaseEnrichmentDecorator — no @Produces needed

    // --- CDI event observers ---

    void onExperienceRecorded(@jakarta.enterprise.event.Observes ExperienceRecorded event,
                               RelationshipProcessor processor) {
        processor.onExperienceRecorded(event);
    }

    void onCbrOutcomeCloudEvent(@ObservesAsync @CloudEventType(CbrEventTypes.CBR_OUTCOME) CloudEvent event,
                                 CbrOutcomeProcessor processor, ObjectMapper objectMapper) {
        if (event.getData() == null) {
            LOG.warnf("CloudEvent %s has no data payload — skipping", event.getId());
            return;
        }
        CbrOutcomeData data;
        try {
            data = objectMapper.readValue(event.getData().toBytes(), CbrOutcomeData.class);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to deserialize CloudEvent %s — skipping", event.getId());
            return;
        }
        processor.onCbrOutcome(data);
    }

    // --- Scheduled retention ---

    @Inject MemoryRetentionPurger retentionPurger;
    @Inject MemoryRetentionConfig retentionConfig;
    @Inject CbrRetentionPurger cbrPurger;
    @Inject CbrRetentionConfig cbrConfig;
    @Inject TrustRetentionPurger trustPurger;
    @Inject TrustRetentionConfig trustConfig;

    void onMemoryRetentionTick() {
        retentionPurger.purgeExpired(retentionConfig.enabled(), retentionConfig.domain(),
                retentionConfig.maxAgeDays(), retentionConfig.minConfidence());
    }

    void onCbrRetentionTick() {
        cbrPurger.purgeExpired(cbrConfig.enabled(), cbrConfig.domain(),
                cbrConfig.caseTypes(), cbrConfig.maxAgeDays(),
                cbrConfig.maxCasesPerType(), cbrConfig.minTrustScore());
    }

    void onTrustRetentionTick() {
        trustPurger.evaluateTrajectories(trustConfig.enabled(), trustConfig.domain(),
                trustConfig.caseTypes(), trustConfig.minCurrentTrust());
    }
}
