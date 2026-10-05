package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognition.appraisal.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class NoOpStrategiesTest {

    @Test
    void noOpSaliencePassesThroughObservation() {
        var strategy = new NoOpSalienceStrategy();
        var context = new SalienceContext("The room is dark", List.of(), null, List.of(), List.of());
        var result = strategy.perceive(context);
        assertThat(result.narrative()).isEqualTo("The room is dark");
        assertThat(result.salience()).isEmpty();
    }

    @Test
    void noOpAppraisalReturnsEmpty() {
        var strategy = new NoOpAppraisalStrategy();
        var situation = PerceivedSituation.passThrough("observation");
        var context = new AppraisalContext(situation, List.of(), null, null, HabituationState.empty(), null);
        var result = strategy.appraise(context);
        assertThat(result.emotions()).isEmpty();
        assertThat(result.actionTendencies()).isEmpty();
    }
}
