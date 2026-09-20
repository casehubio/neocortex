package io.casehub.neocortex.memory.spring;

import io.casehub.memory.runtime.CaseEnrichmentPipeline;
import io.casehub.memory.runtime.MemoryEmitterCore;
import io.casehub.neocortex.memory.CaseEnrichmentStep;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.ExplanationRenderer;
import io.casehub.neocortex.memory.cbr.OutcomeWeightingFunction;
import io.casehub.neocortex.memory.cbr.TrustWeightingFunction;
import io.casehub.neocortex.memory.cbr.runtime.CbrOutcomeProcessor;
import io.casehub.neocortex.memory.cbr.runtime.CbrRetentionPurger;
import io.casehub.neocortex.memory.cbr.runtime.DefaultExplanationRenderer;
import io.casehub.neocortex.memory.cbr.runtime.DefaultOutcomeWeightingFunction;
import io.casehub.neocortex.memory.cbr.runtime.DefaultTrustWeightingFunction;
import io.casehub.neocortex.memory.cbr.runtime.TrustRetentionPurger;
import io.casehub.neocortex.memory.engagement.runtime.EngagementRecorderCore;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.runtime.ExperienceRecorderCore;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.neocortex.memory.reflection.ReflectionSynthesizer;
import io.casehub.neocortex.memory.reflection.runtime.ReflectionOrchestratorCore;
import io.casehub.neocortex.memory.relationship.runtime.RelationshipProcessor;
import io.casehub.neocortex.memory.runtime.MemoryRetentionPurger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass(ExperienceRecorderCore.class)
public class MemoryAutoConfiguration {

    // --- Default implementations ---

    @Bean
    @ConditionalOnMissingBean
    public TrustWeightingFunction trustWeightingFunction(
            @Value("${casehub.cbr.trust-weighting.influence:0.3}") double influence,
            @Value("${casehub.cbr.trust-weighting.trajectory-sensitivity:0.5}") double trajectorySensitivity) {
        return new DefaultTrustWeightingFunction(influence, trajectorySensitivity);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutcomeWeightingFunction outcomeWeightingFunction(
            @Value("${casehub.cbr.outcome-weighting.influence:0.3}") double influence) {
        return new DefaultOutcomeWeightingFunction(influence);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExplanationRenderer explanationRenderer() {
        return new DefaultExplanationRenderer();
    }

    // --- Services ---

    @Bean
    public ExperienceRecorder experienceRecorder(CaseMemoryStore store,
                                                  ApplicationEventPublisher publisher) {
        return new ExperienceRecorderCore(store, event -> publisher.publishEvent(event));
    }

    @Bean
    public EngagementRecorderCore engagementRecorder(CaseMemoryStore store,
                                                      ApplicationEventPublisher publisher) {
        return new EngagementRecorderCore(store, event -> publisher.publishEvent(event));
    }

    @Bean
    public ReflectionOrchestrator reflectionOrchestrator(CaseMemoryStore store,
                                                          ReflectionSynthesizer synthesizer,
                                                          ApplicationEventPublisher publisher) {
        return new ReflectionOrchestratorCore(store, synthesizer, event -> publisher.publishEvent(event));
    }

    @Bean
    public RelationshipProcessor relationshipProcessor(CaseMemoryStore store,
                                                        ApplicationEventPublisher publisher) {
        return new RelationshipProcessor(store, event -> publisher.publishEvent(event));
    }

    @Bean
    public MemoryEmitterCore memoryEmitter(CaseMemoryStore store) {
        return new MemoryEmitterCore(store);
    }

    // --- Retention ---

    @Bean
    public MemoryRetentionPurger memoryRetentionPurger(CaseMemoryStore store) {
        return new MemoryRetentionPurger(store);
    }

    @Bean
    public CbrRetentionPurger cbrRetentionPurger(CbrRecordStore store) {
        return new CbrRetentionPurger(store);
    }

    @Bean
    public TrustRetentionPurger trustRetentionPurger(CbrRecordStore store,
                                                     Optional<AgentTrustProvider> trustProvider) {
        return new TrustRetentionPurger(store, trustProvider.orElse(null));
    }

    @Bean
    public CbrOutcomeProcessor cbrOutcomeProcessor(CbrRecordStore store) {
        return new CbrOutcomeProcessor(store);
    }

    // --- Enrichment pipeline ---

    @Bean
    public CaseEnrichmentPipeline caseEnrichmentPipeline(CaseMemoryStore store,
                                                          List<CaseEnrichmentStep> steps) {
        return new CaseEnrichmentPipeline(store, steps);
    }
}
