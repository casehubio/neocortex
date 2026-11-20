package io.casehub.neocortex.cognition.eval;

import io.casehub.neocortex.cognition.appraisal.ActionReadiness;
import io.casehub.neocortex.cognition.appraisal.AppraisalContext;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.appraisal.PerceivedSituation;
import io.casehub.neocortex.cognition.eval.divergence.DivergenceFixtures;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppraisalStrategyTest {

    private final TestAppraisalStrategy strategy = new TestAppraisalStrategy();

    @Test
    void secDimensionsDivergeByPersonality() {
        var openCtx = contextFor("open",
                DivergenceFixtures.openDefaults().appraisalWeights(),
                DivergenceFixtures.OPEN_AXES);
        var cautiousCtx = contextFor("cautious",
                DivergenceFixtures.cautiousDefaults().appraisalWeights(),
                DivergenceFixtures.CAUTIOUS_AXES);

        var openResult = strategy.appraise(openCtx);
        var cautiousResult = strategy.appraise(cautiousCtx);

        assertThat(openResult.secDimensions()).isNotEmpty();
        assertThat(cautiousResult.secDimensions()).isNotEmpty();

        boolean anyDiffers = false;
        for (var key : openResult.secDimensions().keySet()) {
            if (!openResult.secDimensions().get(key).equals(
                    cautiousResult.secDimensions().get(key))) {
                anyDiffers = true;
                break;
            }
        }
        assertThat(anyDiffers)
                .as("SEC dimensions should differ — open=%s vs cautious=%s",
                        openResult.secDimensions(), cautiousResult.secDimensions())
                .isTrue();
    }

    @Test
    void actionTendenciesDivergeByDisposition() {
        var openCtx = contextFor("open",
                DivergenceFixtures.openDefaults().appraisalWeights(),
                DivergenceFixtures.OPEN_AXES);
        var cautiousCtx = contextFor("cautious",
                DivergenceFixtures.cautiousDefaults().appraisalWeights(),
                DivergenceFixtures.CAUTIOUS_AXES);

        var openResult = strategy.appraise(openCtx);
        var cautiousResult = strategy.appraise(cautiousCtx);

        assertThat(openResult.actionTendencies()).isNotEmpty();
        assertThat(cautiousResult.actionTendencies()).isNotEmpty();

        assertThat(openResult.actionTendencies().getFirst().readiness())
                .as("cooperative disposition → APPROACH")
                .isEqualTo(ActionReadiness.APPROACH);
        assertThat(cautiousResult.actionTendencies().getFirst().readiness())
                .as("avoidant disposition → AVOIDANCE")
                .isEqualTo(ActionReadiness.AVOIDANCE);
    }

    private static AppraisalContext contextFor(String agentId,
            AppraisalWeights weights,
            DispositionAxes axes) {
        return new AppraisalContext(agentId,
                PerceivedSituation.passThrough("test observation"),
                List.of(), weights, null, HabituationState.empty(), null, axes);
    }
}
