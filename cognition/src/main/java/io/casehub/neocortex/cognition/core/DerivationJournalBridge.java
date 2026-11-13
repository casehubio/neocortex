package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognition.journal.EventRef;
import io.casehub.neocortex.cognitive.observability.DerivationObserver;

import java.time.Instant;

public class DerivationJournalBridge implements DerivationObserver {

    private final CognitiveJournal journal;
    private final String tenantId;

    public DerivationJournalBridge(CognitiveJournal journal, String tenantId) {
        this.journal = journal;
        this.tenantId = tenantId;
    }

    @Override
    public void onPipelineStart(String agentId, String eventRef, PipelineInput input) {
        journal.record(new CognitiveEntry.DerivationStarted(
                tenantId, agentId, EventRef.parse(eventRef), Instant.now(),
                input.axes(), input.profileTerms(), input.facetNames(), input.goals()));
    }

    @Override
    public void onStageComputation(String agentId, String eventRef, StageComputation stage) {
        journal.record(new CognitiveEntry.StageComputed(
                tenantId, agentId, EventRef.parse(eventRef), Instant.now(),
                stage.method(), stage.baseValues(),
                stage.facetEffects().stream()
                        .map(fe -> new io.casehub.neocortex.cognition.journal.FacetEffect(
                                fe.facet(), fe.facetScore(), fe.field(), fe.before(), fe.after()))
                        .toList(),
                stage.finalValues()));
    }

    @Override
    public void onDataFlow(String agentId, String eventRef, DataFlow flow) {
        journal.record(new CognitiveEntry.DataFlowed(
                tenantId, agentId, EventRef.parse(eventRef), Instant.now(),
                flow.sourceMethod(), flow.sourceField(),
                flow.targetMethod(), flow.targetField(), flow.value()));
    }

    @Override
    public void onPipelineComplete(String agentId, String eventRef) {
        journal.record(new CognitiveEntry.DerivationCompleted(
                tenantId, agentId, EventRef.parse(eventRef), Instant.now()));
    }
}
