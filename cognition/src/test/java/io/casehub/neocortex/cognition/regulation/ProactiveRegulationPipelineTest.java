package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.core.CognitionPhase;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.MindMapStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProactiveRegulationPipelineTest {

    @Test
    void proactiveParticipantRegisteredInFoundationPhase() {
        var core = new CognitionCore(null, null, null, null, null, null, null, null);
        var mindMapStore = mock(MindMapStore.class);
        var goalOrchestrator = mock(GoalProposalOrchestrator.class);
        var defaultsRegistry = mock(CognitiveDefaultsRegistry.class);

        core.configureProactiveRegulation(
                goalOrchestrator, mindMapStore, defaultsRegistry,
                "cog-sg", ProactiveAvoidanceMode.LOCATION_ONLY, 50, 10.0);

        assertThat(core.proactiveRegulationParticipant()).isNotNull();
        assertThat(core.proactiveRegulationParticipant())
                .isInstanceOf(ProactiveRegulationParticipant.class);
    }

    @Test
    void proactiveParticipantTicksWithoutError() {
        var core = new CognitionCore(null, null, null, null, null, null, null, null);
        var mindMapStore = mock(MindMapStore.class);
        var goalOrchestrator = mock(GoalProposalOrchestrator.class);
        var defaultsRegistry = mock(CognitiveDefaultsRegistry.class);

        core.configureProactiveRegulation(
                goalOrchestrator, mindMapStore, defaultsRegistry,
                "cog-sg", ProactiveAvoidanceMode.LOCATION_ONLY, 50, 10.0);

        var ctx = new CognitionTickContext("a1", "t1", null, null);
        core.proactiveRegulationParticipant().tick(ctx);
        verifyNoInteractions(goalOrchestrator);
    }
}
