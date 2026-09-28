package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.memory.experience.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ActionAppraisalObserverTest {

    private RecordingActionAppraisal appraisal;
    private InMemoryMindMapStore store;
    private ActionAppraisalObserver observer;
    private String goalSubgraphId;

    private static final String TENANT = "t1";

    @BeforeEach
    void setUp() {
        appraisal = new RecordingActionAppraisal();
        store = new InMemoryMindMapStore();
        goalSubgraphId = store.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
        observer = new ActionAppraisalObserver(store, appraisal, null);
    }

    @Test
    void outcomeEvent_selfAppraisal_buildsContextCorrectly() {
        store.addNode(NodeInput.of("planning", goalSubgraphId)
                .withProperties(Map.of("status", "active", "priority", "0.8",
                        "capability", "planning")), TENANT);

        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "completed planning", 0.9,
                Map.of(), "success", "planning");

        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts).hasSize(1);
        var ctx = appraisal.contexts.get(0);
        assertThat(ctx.isSelfAction()).isTrue();
        assertThat(ctx.outcome()).isEqualTo(ActionOutcome.SUCCESS);
        assertThat(ctx.goalRelevance()).isGreaterThan(0.0);
    }

    @Test
    void nonOutcomeEvent_ignored() {
        var action = new Action("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "started task", null, Map.of(), "planning");

        observer.onExperienceRecorded(new ExperienceRecorded(action, "mem-1"));

        assertThat(appraisal.contexts).isEmpty();
    }

    @Test
    void targetAgent_triggersOtherAppraisal() {
        store.addNode(NodeInput.of("research", goalSubgraphId)
                .withProperties(Map.of("status", "active", "priority", "0.6",
                        "capability", "research")), TENANT);

        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "agent-b helped with research", 0.8,
                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "agent-b"),
                "success", "research");

        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts).hasSize(2);
        var selfCtx = appraisal.contexts.stream()
                .filter(ActionContext::isSelfAction).findFirst().orElseThrow();
        var otherCtx = appraisal.contexts.stream()
                .filter(c -> !c.isSelfAction()).findFirst().orElseThrow();
        assertThat(selfCtx.actingAgentId()).isEqualTo("agent-a");
        assertThat(otherCtx.actingAgentId()).isEqualTo("agent-b");
        assertThat(otherCtx.apprasingAgentId()).isEqualTo("agent-a");
    }

    @Test
    void selfReferentialTargetAgent_onlySelfAppraisal() {
        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "self action", 0.8,
                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "agent-a"),
                "done", "planning");

        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts).hasSize(1);
        assertThat(appraisal.contexts.get(0).isSelfAction()).isTrue();
    }

    @Test
    void outcomeStatusMetadata_mapsToActionOutcome() {
        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "did thing", 0.5,
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failure"),
                "partial", "planning");

        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts).hasSize(1);
        assertThat(appraisal.contexts.get(0).outcome()).isEqualTo(ActionOutcome.FAILURE);
    }

    @Test
    void confidenceFallback_highConfidence_mapsToSuccess() {
        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "did thing", 0.9, Map.of(), "done", "planning");

        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts.get(0).outcome()).isEqualTo(ActionOutcome.SUCCESS);
    }

    @Test
    void confidenceFallback_lowConfidence_mapsToFailure() {
        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "did thing", 0.2, Map.of(), "done", "planning");

        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts.get(0).outcome()).isEqualTo(ActionOutcome.FAILURE);
    }

    @Test
    void noGoalMatch_zeroRelevance() {
        store.addNode(NodeInput.of("research", goalSubgraphId)
                .withProperties(Map.of("status", "active", "capability", "research")), TENANT);

        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "unrelated action", 0.9, Map.of(), "done", "swimming");

        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts).hasSize(1);
        assertThat(appraisal.contexts.get(0).goalRelevance()).isEqualTo(0.0);
    }

    @Test
    void nullStore_gracefulDegradation() {
        var obs = new ActionAppraisalObserver((MindMapStore) null, appraisal, null);
        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "desc", 0.9, Map.of(), "done", "planning");

        obs.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts).isEmpty();
    }

    @Test
    void nullAppraisal_gracefulDegradation() {
        var obs = new ActionAppraisalObserver(store, null, null);
        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "desc", 0.9, Map.of(), "done", "planning");

        obs.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));
        // no exception thrown — verified by reaching this line
    }

    @Test
    void failureOutcome_negativeGoalRelevance() {
        store.addNode(NodeInput.of("planning", goalSubgraphId)
                .withProperties(Map.of("status", "active", "priority", "0.7",
                        "capability", "planning")), TENANT);

        var outcome = new Outcome("agent-a", TENANT, "case-1", "turn-1",
                Instant.now(), "planning failed", 0.1,
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failure"),
                "failed", "planning");

        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-1"));

        assertThat(appraisal.contexts).hasSize(1);
        assertThat(appraisal.contexts.get(0).goalRelevance()).isLessThan(0.0);
    }

    static class RecordingActionAppraisal implements ActionAppraisal {
        final List<ActionContext> contexts = new ArrayList<>();

        @Override
        public List<CognitiveEmotion> appraise(ActionContext context) {
            contexts.add(context);
            return List.of();
        }
    }
}
